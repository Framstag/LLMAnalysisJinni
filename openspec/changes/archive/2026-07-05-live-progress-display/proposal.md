## Why

Current console output is a firehose of raw chat messages interleaved across parallel workers, with no live progress, no timing, and no visual structure. Users watch scrolling text with no sense of "how far along" or "what's happening right now." The existing `--execution-trace=false` flag suppresses chat content but leaves flat, static output.

A live terminal UI gives the user immediate situational awareness: which tasks are running, how long each takes, what each worker is doing (request sent, tool call, response received), and aggregate token usage — all updating in real-time.

## What Changes

- **New default console output**: Live TUI showing task list with status icons, running time, per-task token usage, loop progress, and per-worker interaction timelines
- **Progress callback in ChatExecutor**: ChatExecutor reports each interaction step (request sent, response received, tool call, tool result) via a callback interface
- **`--execution-trace` becomes opt-in verbose mode**: Restores current SLF4J console firehose. Disables TUI entirely.
- **Non-TTY fallback**: When stdout is piped or no console is available, output simple sequential status lines (no TUI, no colours, no cursor tricks)
- **Log files unchanged**: Full conversation logs always written to `logs/*.log` regardless of display mode
- **New dependency**: JLine 3 for terminal handling (size detection, colour support, signal handling)

## Capabilities

### New Capabilities
- `live-progress-display`: Real-time terminal UI showing task execution progress, per-worker interaction timelines, loop progress, running time, and token usage. Includes automatic fallback to plain sequential output when no TTY is available.

### Modified Capabilities
- *(none — existing `llm-interaction-logger` spec covers file logging and console trace flag, neither of which change behavior)*

## Impact

- **New files**: `display/ProgressDisplay.java`, `display/TaskRow.java`, `display/LoopWorkerRow.java`, `display/ProgressCallback.java`, `display/SimpleOutput.java`, `display/DisplayManager.java`
- **Modified files**: `pom.xml` (add JLine 3), `AnalyseCmd.java` (integrate display via DisplayManager), `ChatExecutionContext.java` (add callback field), `ChatExecutor.java` (call callbacks at interaction steps), `logback.xml` (console appender conditional on `--execution-trace`)
- **CLI change**: `--execution-trace` defaults to `false` (was `true`). When set, TUI is disabled and verbose SLF4J console output is used.
- **No breaking API changes**: All changes internal to the CLI tool. No public API or config format changes.
