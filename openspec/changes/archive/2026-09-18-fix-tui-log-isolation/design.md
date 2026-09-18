# Design

## Context

See `proposal.md` - Why for the observed failure.

Current state that shapes the approach:

- `logback.xml` declares one appender, a `ConsoleAppender` on `System.out`, attached to the root
  logger with a pattern that includes the level, the logger name and the MDC `taskId`.
- `AnalyseCmd` replaces the root level with `WARN` when the execution trace is off, with a comment
  claiming suppression. `WARN` and `ERROR` therefore still reach `System.out`.
- The TUI writes through the JLine *system* terminal, which is `System.out`. The two writers share
  one file descriptor, and `ProgressDisplay` repaints on a 500 ms timer, so a record emitted by any
  of the parallel task or loop threads lands between two frames.
- `ProgressDisplay` already keeps `lastFrame` and `lastRenderedLines` so a repaint moves the cursor
  back over exactly the region it painted, and it already distinguishes ANSI-capable from dumb
  terminals.
- There is no SLF4J file appender. `ChatLogger` writes per-task conversation files to
  `<workspace>/logs/<taskId>[_<loopIndex>].log`, which is a different artefact with a different
  purpose.
- `CsvReportWriter.writeCsv()` and `JavaTool.createTypeResolver()` write diagnostics with
  `System.err.println`, bypassing logging completely. `CsvReportWriter` is used by file-statistics
  tasks, so those writes do happen during a live TUI run.
- `live-progress-display` already requires that SLF4J console output is suppressed while the TUI is
  the display mode, so this change makes an existing requirement true rather than inventing a new
  one. `JarSmokeIT` requires a `WARN`/`ERROR` console line naming the configuration failure on the
  piped path, which must keep working.

## Goals / Non-Goals

**Goals:**

- Nothing writes to the terminal while the TUI owns it: not logback, not tool code.
- The diagnostics that were previously interleaved into frames stay available after the run, in a
  file inside the workspace.
- A warning or error that is not tied to one task row stays visible during the run.
- Non-TUI behaviour is unchanged, including the console diagnostic the artefact smoke test asserts.

**Non-Goals:**

- No log rotation, no retention policy, no configurable log level or log path. One file per run.
- No log viewer, no key binding, no scrolling inside the TUI. The frame shows the latest record
  only.
- No change to `ChatLogger`'s per-task conversation files.
- No change to what is logged, only to where it goes. INFO-level engine logs keep their current
  content.

## Decisions

### The routing decision lives in `AnalyseCmd`, where the display mode is already decided

Terminal ownership starts with the first painted frame, not with the start of the process. The
routing is therefore installed immediately before the display is created, and diagnostics emitted
before that point stay on the console. The console appender itself is detached only after that first
frame has been painted, so a diagnostic the routing produces on its own - an engine log file that
cannot be created or opened - still reaches the console instead of being forwarded to a display that
does not exist yet. That boundary is deliberate: the display decision needs the terminal probe,
which a run with nothing to execute must not perform at all, and the configuration of the run has to
be readable before the mode is known - a configuration failure has to land on the console, which is
what the artefact smoke test asserts.

The existing root-level adjustment is replaced by a small programmatic logback reconfiguration next
to it, because that is the first point where both the workspace path and the display decision are
known. It has two steps, called around the creation of the display:

```
installForDisplayMode      attach the engine log file and in-frame appenders, keep the console
display created            first frame painted, display registered as sink target
detachConsoleAfterFirstFrame   drop the console appenders, the display shows the records from here
```

- **TUI branch** — leave the root level at `INFO` (so the file can record what the console used to
  show), detach the console appender, attach a file appender for `<workspace>/logs/engine.log`
  (`append=false`, same pattern as today) and attach the in-frame appender at `WARN` level.
- **Non-TUI branch** — keep today's behaviour: console appender stays attached and the root level
  is lowered to `WARN`.

Alternatives considered:

- *Conditional logback configuration in `logback.xml`* — needs the janino expression evaluator,
  which is a new dependency, and the workspace path is still unknown at configuration time.
- *A system property pointing at the log file, read by `logback.xml`* — logback initialises on the
  first logger call, which happens before the workspace configuration is loaded, and the TUI
  decision requires probing the terminal, which the run deliberately postpones until it knows there
  is work.
- *Raising the root level to `ERROR` or `OFF` instead of detaching* — suppresses the diagnostics
  instead of relocating them, and does not stop `System.err` writes by tool code.

### The in-frame log line is fed by a logback appender, not polled

A logback appender attached only in the TUI branch forwards `ILoggingEvent`s to a minimal sink
interface implemented by `ProgressDisplay` (level, logger name, the MDC `taskId` when set,
formatted message). `DisplayManager` passes the sink to whatever display is active and is a no-op
otherwise. Alternatives considered:

- *Ring buffer plus polling from the render timer* — adds a second timing dependency and delays the
  line by up to one repaint interval for no benefit.
- *Capturing `System.out`* — would also capture the TUI's own rendering, and would not survive
  JLine's own use of the stream.
- *Showing the message on the task row instead* — the task row already carries a task's own failure
  message; a condition without a task (for example a JSON correction in a worker) has no row.

### The reserved line is painted on the record, and once more when the display closes

The render timer alone is not enough: it paints every 500 ms, so a record that arrives in the last
tick before the run ends is never shown. A manual run proved it - the record reached the log file
0.3 s before the process ended, and no frame carried it. The display therefore paints in
`onLogLine`, spaced by a debounce so that a burst of records (one per schema violation) does not
paint a frame per record, and `close()` paints one final frame for whatever the debounce held back,
before it prints the summary. A frame that did not change is still not written again, which the
existing frame comparison already guarantees.

Alternatives considered:

- *Paint on every record* — correct but thrashes the terminal during a violation burst.
- *Paint only in `close()`* — the record would be missing from every frame during the run and would
  only appear in the final one.
- *Polling from the render timer only* — this is what the manual run disproved.

### One reserved frame row, part of the existing frame accounting

The frame reserves one line for the latest `WARN`/`ERROR` (for example `! [LicenseEvaluation]
<message>`), truncated to the available width. The line is always present, empty when nothing was
reported, so the number of lines a frame occupies never changes because of logging, and
`lastRenderedLines` needs no special case. On a dumb terminal the line is rendered like every other
frame line, with no cursor manipulation, which keeps the existing terminal-capability rules intact.

### Tool diagnostics move to SLF4J

`CsvReportWriter` and `JavaTool.createTypeResolver` get loggers and emit the same message text at
the same severity (`ERROR` for both current cases, since both report a failure to produce a
result). For non-TUI runs the visible result is unchanged; for TUI runs it is routed like every
other record. Alternative considered: threading the display into the tools — rejected, the tools
have no business depending on the display layer.

### The engine log file is written only in TUI mode

`<workspace>/logs/engine.log` is a stand-in for the console, so it exists exactly when the console
is unavailable. In non-TUI modes the console stays the diagnostic channel, which keeps one log
destination per run and keeps the smoke test's expectation untouched. The file name stays clear of
`ChatLogger`'s `<taskId>[_<loopIndex>].log` pattern as long as no task is called `engine`; there is
no such task in either domain today. The directory is created if missing, as `ChatLogger` already
does.

### Tests

- Unit test for the routing: with the TUI branch applied in a temporary workspace, a record at
  `WARN` reaches the file (level and task identifier included) and reaches the sink, and the console
  appender is no longer attached to the root logger - that detachment is the mechanism that keeps
  records off the terminal.
- Unit test for the frame: `ProgressDisplayTest` gains cases for a frame with no record (row
  present but empty), with one record, with a record longer than the width (truncated), and for two
  consecutive records (later one wins, line count unchanged).
- Unit test for the tool diagnostics: the message a failing CSV write produces is emitted through
  the logger, not through the error stream (a log-capturing appender plus an assertion that the
  captured error stream is empty), and an unreadable JAR dependency directory is reported the same
  way.
- The artefact smoke test is not changed and must stay green, which is the evidence that the piped
  path kept its console diagnostics.

## Risks / Trade-offs

- **Frame layout change.** One more line changes every frame; `ProgressDisplayTest` expectations
  must be updated deliberately rather than adjusted until green, otherwise the row accounting can
  silently break the "repaint exactly its own region" rule.
- **A record can still be missed by a frame that is already on screen.** The debounce delays a burst
  member, and the final paint covers everything that arrived before `close()`. A record emitted after
  `close()` is in the log file only, which is the intended end of the run.
- **No destination is missing at any point.** Before the first frame the console is attached, after
  it the file and the frame line are. A diagnostic produced by the routing itself therefore always
  has somewhere to go; a buffering sink was considered instead and rejected as the more complex way
  to reach the same guarantee.
- **Reconfiguration ordering.** The reconfiguration must happen before the TUI starts and only on
  the TUI path. If it happens on the piped path, `JarSmokeIT` loses its diagnostic and fails - which
  is the intended tripwire.
- **Logback's own status output.** Logback writes internal errors to the console independently of
  the appenders. A misconfigured appender could therefore still print a line; the run must not fail
  because of it, and appender errors are not a task failure.
- **Read-only or full workspace.** If the log file cannot be opened, the run must continue with no
  file destination rather than abort. The appender failure is a logback-internal condition, and the
  in-frame line still works because it needs no file.
- **INFO into a file.** The file will contain tool arguments and response payloads that the console
  only showed in trace mode. Per-task `ChatLogger` files already contain the same material in the
  same directory, so this adds no new exposure, but it does mean the file is as sensitive as the
  rest of `logs/`.
- **Concurrent runs in one workspace** overwrite each other's `engine.log`, as they already
  overwrite each other's per-task logs.
