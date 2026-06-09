## Why

Loop analysis tasks (e.g., iterating over modules in `/modules/modules`) currently execute sequentially — one LLM call per loop index, one after another. For projects with many modules, this is slow. Parallelizing loop execution across a thread pool dramatically reduces wall-clock time while preserving restartability.

## What Changes

- **TaskState**: Replace `lastSuccessfulIndex` (single Integer, overwritten) with `successfulIndices` (Set<Integer>, additive). Worker threads complete indices out of order; set tracks which are done.
- **TaskManager**: Replace single-index tracking with `isIndexSuccessful(task, index)` query and thread-safe `synchronized markIndexSuccessful(task, index)`. Add `markLoopTaskAsSuccessful(task)` that clears the set when all indices are done.
- **StateManager**: Add `getLoopArraySize()` and `loopAtIndex(index)` (positional jump, no shared iterator). `updateLoopState` takes index instead of relying on shared `loopValue`. `saveState()` synchronized for concurrent worker writes.
- **AnalyseCmd**: Replace sequential `while canLoop()` with parallel dispatch loop: iterate indices, skip completed, submit uncompleted to `ExecutorService`, await all.
- **Config**: Add `loopParallelism` setting (int, default 1) to control thread pool size.

## Capabilities

### New Capabilities

- `loop-parallelism`: Engine-level support for concurrent execution of loop task iterations using a configurable thread pool. No new analysis capabilities — changes how the loop execution model works internally.

### Modified Capabilities

None. All existing analysis capabilities (boolean params, CSV reports, coupling analysis, etc.) continue to produce identical results, just faster when loopParallelism > 1.

## Impact

- `src/main/java/com/framstag/llmaj/tasks/TaskState.java` — data model change
- `src/main/java/com/framstag/llmaj/tasks/TaskManager.java` — new query/mutate methods, thread-safe state
- `src/main/java/com/framstag/llmaj/state/StateManager.java` — indexed loop access, synchronized save
- `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java` — parallel dispatch logic
- `src/main/java/com/framstag/llmaj/config/Config.java` — new loopParallelism property
- Workspace `config.yml` — optional loopParallelism key