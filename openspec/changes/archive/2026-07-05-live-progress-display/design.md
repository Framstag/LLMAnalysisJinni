## Context

Current console output uses SLF4J + Logback for all logging. Chat messages, tool calls, and results are logged via `logger.info()` calls in `ChatExecutor` and `ChatLogger`. Under parallel loop execution, output from multiple threads interleaves chaotically. The `--execution-trace=false` flag suppresses chat content but leaves flat, static output with no live updates, timing, or visual structure.

The existing `ChatLogger` handles two concerns: (1) progressive console output of chat messages, and (2) file logging of full conversations. This design separates console display from file logging, replacing the console concern with a live TUI while keeping file logging unchanged.

```
BEFORE:

SLF4J → Console (all messages, interleaved)
SLF4J → File (full conversations)

AFTER (default):

SLF4J → File (full conversations, always-on)
ProgressDisplay → Terminal (live TUI, no chat content)

AFTER (--execution-trace):

SLF4J → Console (verbose, current behaviour)
SLF4J → File (full conversations, always-on)
```

## Goals / Non-Goals

**Goals:**
- Live TUI showing all DAG tasks with real-time status updates
- Per-worker interaction timeline for parallel loop tasks
- Running time per task (live updating)
- Per-task and aggregate token usage display
- Loop progress (e.g., "4/12 modules")
- Inline error display on failed task rows
- Intuitive colour scheme (green=done, cyan=running, red=failed, dim=pending)
- Non-TTY fallback to simple sequential status lines
- `--execution-trace` flag restores current verbose SLF4J console output
- Log files always written regardless of display mode

**Non-Goals:**
- Streaming LLM output (future work)
- Replacing SLF4J entirely (still used for file logging, errors, and `--execution-trace` mode)
- Changing the task scheduling or execution logic
- Adding JSON or structured logging
- Interactive input during analysis (read-only display)

## Decisions

### Decision 1: JLine 3 for terminal handling

JLine 3 provides terminal detection, size queries, colour support, and SIGWINCH handling in a single well-maintained library. It auto-detects whether stdout is a TTY and whether the terminal supports colour.

**Alternatives considered:**
- **Jansi** — lighter (~100KB) but requires manual terminal detection and size queries. No SIGWINCH support.
- **Pure ANSI escape codes** — zero dependencies but fragile across terminal emulators. No terminal detection, no resize handling.
- **Lanterna** — full TUI framework, overkill for a read-only display. Much heavier.

### Decision 2: Callback interface for ChatExecutor progress reporting

A `ProgressCallback` interface is passed through `ChatExecutionContext` to each `ChatExecutor` instance. The executor calls `callback.onRequestSent()`, `callback.onResponseReceived()`, `callback.onToolCall()`, `callback.onToolResult()`, `callback.onTokenUsage()`, and `callback.onComplete()` at the appropriate points in `executeMessages()`.

The `ProgressDisplay` implements this interface. Each callback method is synchronized and updates the display model, then triggers a render.

**Alternatives considered:**
- **Event bus** — more flexible but adds complexity. No other consumers of these events exist today.
- **Shared state polling** — ChatExecutor writes to a shared object, display polls on a timer. Adds latency and complexity. Callbacks are immediate and precise.
- **ThreadLocal** — ChatExecutor stores progress in a ThreadLocal, display reads it. Fragile, breaks if executor thread changes.

### Decision 3: Full redraw on each render cycle

On each render cycle, the TUI moves the cursor to the top of the display area and redraws the entire block. This is simpler than incremental updates and handles variable-length content (e.g., error messages, changing number of worker rows) without tracking what changed.

Render is triggered by:
- Any callback invocation (task state change, interaction step)
- A ScheduledExecutorService timer every 500ms (for elapsed time ticks)

**Alternatives considered:**
- **Incremental update** — only redraw changed rows. More efficient but significantly more complex. Terminal I/O is fast enough that full redraw at 500ms intervals is not a bottleneck.

### Decision 4: Display model is a synchronized data structure

`ProgressDisplay` maintains a list of `TaskRow` objects and a list of `LoopWorkerRow` objects. All public methods are `synchronized`. The render loop reads the model under the same lock.

```
ProgressDisplay
  ├── List<TaskRow> tasks          (synchronized)
  ├── List<LoopWorkerRow> workers  (synchronized)
  ├── TokenUsage aggregateTokens   (synchronized)
  ├── Instant startTime            (immutable after init)
  └── ScheduledExecutorService timer
```

**Thread safety model:**
- Main thread: adds tasks, transitions states
- Worker threads: update worker interaction timeline, token usage
- Timer thread: reads model, renders

All writes and reads go through `synchronized` methods. Render is a read-only snapshot under the same lock.

### Decision 5: Interaction timeline uses Unicode symbols with per-symbol colours

The per-worker interaction timeline uses distinct Unicode symbols and colours for each interaction type:

| Symbol | Meaning | Colour (past) | Colour (current) |
|--------|---------|---------------|------------------|
| `→` | Request sent to LLM | Dim yellow | Bright yellow |
| `←` | Response received from LLM | Dim cyan | Bright cyan |
| `◆` | Tool call executed | Dim magenta | Bright magenta |
| `✓` | Tool result received | Dim green | Bright green |

Past steps are dimmed. The current/last step is highlighted in its bright colour. This gives a live sense of "what's happening right now."

If the terminal encoding does not support Unicode, fall back to ASCII equivalents: `->`, `<-`, `!`, `ok`.

### Decision 6: Non-TTY fallback is a separate output mode

When `System.console() == null` (piped output, CI environment), the system does not attempt TUI rendering. Instead, it prints one sequential status line per event:

```
✓ tool/version                 0.3s
✓ readme-locate               0.5s
▶ module-analysis             2.1s  [4/12]
  ✓ module-core               2.1s
  ✓ module-api                1.8s
  ▶ module-cli                0.9s
  … module-db                 pending
```

No cursor manipulation, no colour, no progress bars. Each line is printed once via `System.out.println()`.

### Decision 7: `--execution-trace` flag inverts default

The `--execution-trace` flag in `AnalyseCmd` now defaults to `false` (was `true`). When set to `true`, the TUI is disabled and the old SLF4J console output behaviour is used. This is a clean separation — no mixing of TUI and verbose text output.

The `Config` class retains `isExecutionTrace()` and `setExecutionTrace()`. When `--execution-trace` is not active, `AnalyseCmd` programmatically sets the root logger level to `WARN` to suppress INFO-level console output. Log files are unaffected — they always write at the configured level.

### Decision 8: ProgressDisplay is AutoCloseable

`ProgressDisplay` implements `AutoCloseable`. On `close()`:
1. The render timer is cancelled
2. The cursor is restored to normal (show cursor, reset colours)
3. A final static summary is printed (all tasks with final status and timing)

This ensures clean terminal state even if analysis is interrupted.

## Risks / Trade-offs

[Terminal compatibility] → JLine 3 handles most terminals, but some edge cases (Windows Command Prompt, ancient terminals) may have issues. Mitigation: JLine auto-detects and falls back to plain mode. Non-TTY fallback covers piped/CI output.

[Render flicker on fast updates] → Full redraw every 500ms may cause visible flicker on slow terminals. Mitigation: use alternate screen buffer (JLine `terminal.enterRawMode()`) or buffer writes and flush atomically. If flicker is observed, increase render interval or switch to incremental updates.

[Callback overhead in ChatExecutor] → Each interaction step calls a synchronized method on ProgressDisplay. This adds negligible overhead compared to LLM call latency (seconds vs microseconds). Acceptable.

[Parallel loop worker count exceeds terminal height] → If the terminal is too short to show all workers, the TUI shows as many as fit and indicates overflow (e.g., "+3 more"). The header and footer are always visible.

[Error messages may be long] → Inline error display truncates to terminal width. Full error is in log file. Acceptable.

[`--execution-trace` default change is a breaking UX change] → Users who relied on verbose console output will need to add `--execution-trace=true`. Mitigation: document prominently in release notes and `--help` output.

## Open Questions

- **Alternate screen buffer**: Should the TUI use the terminal's alternate screen buffer (like `top`, `less`)? This would leave no trace in terminal history after completion. Or use the main buffer and overwrite? Main buffer is more transparent (scrollback works) but leaves partial renders if interrupted. Decision: use main buffer with `AutoCloseable` cleanup. If flicker is an issue, switch to alternate buffer.

- **Progress bar on loop workers**: The interaction timeline shows conversation steps, but should there also be a time-based progress bar per worker? E.g., `[→ ◆✓ → ←] ████░░ 2.1s`. Or is the timeline sufficient? Decision: timeline only for now. Add progress bars if users request them.

- **Token usage during execution**: Token usage is only known after each LLM response. Should the TUI show "IN: ? OUT: ?" for running tasks and update when known? Or only show on completion? Decision: show on completion only, to avoid flickering partial values.
