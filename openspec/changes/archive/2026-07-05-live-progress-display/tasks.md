## 1. Dependency Setup

- [x] 1.1 Add JLine 3 dependency to `pom.xml` (`org.jline:jline-terminal:3.29.0`)
- [x] 1.2 Verify JLine 3 shades correctly in the uber-jar (no conflicts) — added shade filters to keep ServiceLoader-loaded classes

## 2. ProgressCallback Interface

- [x] 2.1 Create `display/ProgressCallback.java` with methods: `onRequestSent`, `onResponseReceived`, `onToolCall`, `onToolResult`, `onTokenUsage`, `onComplete`, `onError`
- [x] 2.2 Add `ProgressCallback` field to `ChatExecutionContext` with getter/setter
- [x] 2.3 Wire callbacks into `ChatExecutor.executeMessages()` at each interaction step
- [x] 2.4 Pass callback through in `AnalyseCmd` for both loop and non-loop execution paths

## 3. Display Model

- [x] 3.1 Create `display/TaskRow.java` — model for one task row (id, name, status, elapsed time, token usage, error message)
- [x] 3.2 Create `display/LoopWorkerRow.java` — model for one loop worker (index, label, status, interaction steps list, round count, elapsed time)
- [x] 3.3 Create `display/Ansi.java` — ANSI escape code constants and helpers (colours, cursor movement, reset)

## 4. ProgressDisplay Core

- [x] 4.1 Create `display/ProgressDisplay.java` implementing `ProgressCallback` and `AutoCloseable`
- [x] 4.2 Implement synchronized display model (task list, worker list, aggregate token usage, start time)
- [x] 4.3 Implement `addTasks()`, `startTask()`, `completeTask()`, `failTask()` methods
- [x] 4.4 Implement `addLoopWorker()`, `updateLoopWorker()`, `completeLoopWorker()` methods
- [x] 4.5 Implement `updateTokenUsage()` for per-task and aggregate tracking
- [x] 4.6 Implement render loop with full redraw on each cycle
- [x] 4.7 Implement 500ms ScheduledExecutorService timer for elapsed time updates
- [x] 4.8 Implement `close()` — cancel timer, restore cursor, print final summary

## 5. TUI Rendering

- [x] 5.1 Render header: project name, model info, elapsed time
- [x] 5.2 Render task list with status icons, colours, elapsed time, token usage
- [x] 5.3 Render loop parent row with loop progress counter (e.g., "4/12")
- [x] 5.4 Render worker sub-rows with interaction timeline `[→ ⚡✓ → ←]` and round count
- [x] 5.5 Render inline error message on failed task rows (truncated to terminal width)
- [x] 5.6 Render footer: aggregate token usage, total elapsed time
- [x] 5.7 Handle terminal too small — show as many rows as fit, indicate overflow
- [x] 5.8 Handle terminal resize (SIGWINCH) — handled by 500ms render timer

## 6. Non-TTY Fallback

- [x] 6.1 Detect `System.console() == null` or non-TTY stdout
- [x] 6.2 Implement `SimpleOutput` class that prints one sequential status line per event
- [x] 6.3 Route to `SimpleOutput` instead of `ProgressDisplay` when no TTY (via `DisplayManager` facade)

## 7. `--execution-trace` Mode

- [x] 7.1 Change `--execution-trace` default in `AnalyseCmd` from `true` to `false`
- [x] 7.2 When `--execution-trace=true`, skip TUI init and use SLF4J console output
- [x] 7.3 Suppress SLF4J INFO console output when not in execution-trace mode (programmatic root level)
- [x] 7.4 Set root logger level in `AnalyseCmd` when `--execution-trace` is active

## 8. DisplayManager Facade

- [x] 8.1 Create `display/DisplayManager.java` — unified facade that routes to `ProgressDisplay` (TUI), `SimpleOutput` (piped/CI), or no-op (execution-trace)
- [x] 8.2 `DisplayManager` constructor takes `Config`, `useTui`, `executionTrace`, task list, and pre-completed task ID set
- [x] 8.3 Expose `getCallback()` for `ChatExecutor` progress reporting
- [x] 8.4 Provide `onTaskStart/onTaskComplete/onTaskError/setLoopTotal` methods — hide all `if/else` branching
- [x] 8.5 Implement `AutoCloseable` for unified cleanup

## 9. AnalyseCmd Integration

- [x] 9.1 Initialize `DisplayManager` at start of `call()` instead of raw `ProgressDisplay`/`SimpleOutput`
- [x] 9.2 Replace all inline `if (display != null) ... else if (simple != null)` blocks with `displayManager.method()` calls
- [x] 9.3 Call `displayManager.onTaskStart()` / `displayManager.onTaskComplete()` around each task execution
- [x] 9.4 Call `displayManager.getCallback().onWorkerStart/onWorkerComplete/onWorkerError()` for loop workers
- [x] 9.5 Wrap execution in try-finally for `displayManager.close()`

## 9. Testing

- [x] 9.1 Unit test `ProgressCallback` interface contract (tested via `SimpleOutput` callback routing)
- [x] 9.2 Unit test display model updates (TaskRow, LoopWorkerRow state transitions)
- [x] 9.3 Manual test: non-TTY fallback detection with `| cat` piping
- [x] 9.4 Unit test `--execution-trace` flag routing (covered by `ChatExecutionLoggingTest`)
- [x] 9.5 Manual test: verify TUI renders correctly with various terminal sizes
- [x] 9.6 Manual test: verify non-TTY output with `| cat` piping
- [x] 9.7 Manual test: verify `--execution-trace` restores old verbose output
