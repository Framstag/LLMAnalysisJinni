## 1. Data Model — TaskState

- [x] 1.1 Replace `Integer lastSuccessfulIndex` with `Set<Integer> successfulIndices` in the TaskState record
- [x] 1.2 Add `@JsonInclude(JsonInclude.Include.NON_EMPTY)` to `successfulIndices` to omit when null/empty
- [x] 1.3 Verify serialization round-trip (Jackson YAML) with empty set, non-empty set, and null

## 2. TaskManager — Index Queries & Thread-Safe Writes

- [x] 2.1 Add `isIndexSuccessful(TaskDefinition task, int index)` — reads from `successfulIndices` set, returns boolean
- [x] 2.2 Add `synchronized markIndexSuccessful(TaskDefinition task, int index)` — adds index to existing set (or creates new HashSet), persists new TaskState via `saveState()`
- [x] 2.3 Add `markLoopTaskAsSuccessful(TaskDefinition task)` — calls `markTaskAsSuccessful(task)` which clears successfulIndices
- [x] 2.4 Remove old `markTaskAsLoopProcessing(task, index)` and `getTaskSuccessFullLoopIndex(task)` methods

## 3. StateManager — Indexed Loop Access

- [x] 3.1 Add `getLoopArraySize()` — returns `loopPos.size()` (0 if no loop active)
- [x] 3.2 Add `loopAtIndex(int index)` — sets `loopValue = loopPos.get(index)` for worker access (positional, no iterator)
- [x] 3.3 Change `updateLoopState(String path, JsonNode value)` to `updateLoopState(int index, String path, JsonNode value)` using `loopPos.get(index)` instead of `loopValue`
- [x] 3.4 Make `saveState()` a `synchronized` method

## 4. Config — loopParallelism Property

- [x] 4.1 Add `int loopParallelism = 1` field with getter/setter
- [x] 4.2 Add to `dumpToLog()` output
- [x] 4.3 Add to `toString()` output

## 5. AnalyseCmd — Parallel Dispatch

- [x] 5.1 Replace sequential `while (stateManager.canLoop())` loop with parallel dispatch:
  - `startLoop()` → read array size → fetch config's `loopParallelism`
  - Create `ExecutorService` with `Executors.newFixedThreadPool(parallelism)`
  - For each index: skip if `isIndexSuccessful`, else `submit` to executor
  - Worker lambda: `loopAtIndex(idx)`, resolve messages, execute chat, updateLoopState(idx, ...), markIndexSuccessful
  - `CompletableFuture.allOf(...).join()` → shutdown executor → `endLoop()`
- [x] 5.2 Call `markLoopTaskAsSuccessful(task)` after loop completes (instead of old `markTaskAsSuccessful`)

## 6. Cleanup & Verify

- [x] 6.1 Remove unused `canLoop()`, `loopNext()`, `getLoopIndex()` from StateManager
- [x] 6.2 Verify build: `mvn verify -DskipTests`
- [x] 6.3 Run existing tests: `mvn verify`
- [x] 6.4 Manual test with `loopParallelism=1` — behaves identically
- [x] 6.5 Manual test with `loopParallelism=4` — parallelism works
- [x] 6.6 Manual test restart — only uncompleted indices re-execute