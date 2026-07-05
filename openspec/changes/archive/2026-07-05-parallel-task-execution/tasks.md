## 1. Config — taskParallelism Setting

- [x] 1.1 Add `taskParallelism` field to `Config.java` (default 2)
- [x] 1.2 Add getter/setter and include in `dumpToLog()`
- [x] 1.3 Add `--task-parallelism` CLI option to `AnalyseCmd`

## 2. TaskManager — Runnable Task Query & Thread Safety

- [x] 2.1 Add `getRunnableTasks()` method returning `List<TaskDefinition>` — all pending tasks whose `dependsOn` tags are in `scheduledTags`
- [x] 2.2 Make `markTaskAsSuccessful()` synchronized
- [x] 2.3 Make `markTaskAsFailed()` synchronized
- [x] 2.4 Make `markLoopTaskAsSuccessful()` synchronized (delegates to `markTaskAsSuccessful`)
- [x] 2.5 Verify `markIndexSuccessful()` is already synchronized (it is)

## 3. StateManager — Thread-Safe State Updates

- [x] 3.1 Make `updateState()` synchronized
- [x] 3.2 Make `updateLoopState()` synchronized
- [x] 3.3 Verify `saveState()` is already synchronized (it is)
- [x] 3.4 Verify `startLoop()` / `endLoop()` are safe (called from single DAG task thread)

## 4. AnalyseCmd — Parallel Dispatch Loop

- [x] 4.1 Create DAG-level `ExecutorService` sized by `config.getTaskParallelism()`
- [x] 4.2 Create `BlockingQueue<String> completionQueue` for worker-to-scheduler signalling
- [x] 4.3 Replace sequential `while (hasPendingTasks) { getNextTask(); execute; markDone }` with scheduler loop:
  - Query `getRunnableTasks()`, submit all not-already-running to pool
  - Block on `completionQueue.take()` for task completion
  - On completion, check for newly unblocked tasks
  - Exit when no pending tasks remain
- [x] 4.4 Extract task execution into a `Runnable` or method that handles both loop and non-loop tasks
- [x] 4.5 Wrap task execution in `synchronized (taskManager) { markTaskAsSuccessful/Failed }`
- [x] 4.6 Handle `--single-step`: submit one task, wait for completion, break
- [x] 4.7 Handle shutdown: `executor.shutdown()` after all tasks complete
- [x] 4.8 Wire `DisplayManager` calls (`onTaskStart`/`onTaskComplete`/`onTaskError`) from worker threads

## 5. MDC Logging

- [x] 5.1 Set `MDC.put("taskId", task.getId())` before task execution in worker
- [x] 5.2 Set `MDC.put("loopIndex", String.valueOf(index))` before loop worker execution
- [x] 5.3 Clear MDC in `finally` block after task/worker execution (`MDC.clear()`)
- [x] 5.4 Update `logback.xml` pattern to include `[%X{taskId}]`

## 6. Testing

- [x] 6.1 Unit test `TaskManager.getRunnableTasks()` — verify it returns only tasks with satisfied deps
- [x] 6.2 Unit test thread safety of `markTaskAsSuccessful()` under concurrent calls
- [x] 6.3 Unit test `StateManager.updateState()` thread safety under concurrent calls
- [x] 6.4 Integration test: run analysis with `--task-parallelism=2` and verify all tasks complete
- [x] 6.5 Integration test: run with `--single-step` in parallel mode and verify only one task executes
- [x] 6.6 Manual test: verify execution-trace output shows task IDs via MDC
- [x] 6.7 Manual test: verify no deadlock with loop tasks under parallel DAG execution
