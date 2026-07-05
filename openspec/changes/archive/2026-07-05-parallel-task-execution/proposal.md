## Why

The current analysis pipeline executes tasks sequentially: one task at a time, in dependency order. Even when multiple tasks have all their dependencies satisfied, they wait in line. For a typical software-architecture analysis with 20+ tasks and multiple independent branches, this wastes significant time — LLM calls are the bottleneck, and they could be running in parallel.

The existing loop-level parallelism (parallel workers within a single loop task) proves the thread-safety patterns work. This change extends that to the DAG level: tasks whose dependencies are met execute concurrently.

## What Changes

- **Parallel DAG execution**: Replace the sequential `while (hasPendingTasks) { getNextTask(); execute; markDone }` loop with a scheduler that submits all runnable tasks to a thread pool
- **Dependency-aware scheduling**: A task runs only when all its `dependsOn` tags are satisfied. The scheduler detects newly-unblocked tasks after each completion
- **Two-tier thread pools**: Separate pools for DAG-level tasks and per-task loop workers — no deadlock risk
- **Thread-safe state**: `StateManager.updateState()` and `TaskManager.markTaskAsSuccessful()` become `synchronized` for concurrent worker access
- **MDC logging**: Every log line tagged with task ID (and loop index where applicable) via SLF4J MDC, so execution-trace output is attributable even with interleaved threads
- **`--single-step` preserved**: Executes exactly one task (including its loop indices) then stops

## Capabilities

### New Capabilities
- `parallel-task-execution`: DAG-level parallel task execution with dependency-aware scheduling

### Modified Capabilities
- `live-progress-display`: TUI already handles parallel task updates via synchronized ProgressDisplay — no changes needed
- `llm-interaction-logger`: MDC context enriches log output with task identity

## Impact

- **Modified files**: `TaskManager.java` (add `getRunnableTasks()`, synchronize mutations), `StateManager.java` (synchronize state updates), `AnalyseCmd.java` (parallel dispatch loop), `Config.java` (add `taskParallelism` setting), `logback.xml` (add MDC pattern)
- **No new files**: All changes are modifications to existing classes
- **No new dependencies**: Uses existing `java.util.concurrent` infrastructure
- **CLI change**: New `--task-parallelism` option (default: 2)
- **No breaking API changes**: All changes internal to the CLI tool
