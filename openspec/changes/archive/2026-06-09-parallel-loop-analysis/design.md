## Context

The analysis engine executes YAML-defined tasks, some of which loop over arrays in the analysis state (e.g., `/modules/modules`). Currently, each loop iteration runs sequentially — one LLM call per index, blocking on completion before advancing. For projects with 20+ modules, this means 20+ serial LLM calls per loop task.

The existing restart mechanism uses a single `lastSuccessfulIndex` integer in TaskState. On restart, all indices ≤ that value are skipped. The sequential iterator (`StateManager.loopNext()` + `Iterator<JsonNode>`) is stateful and single-threaded.

## Goals / Non-Goals

**Goals:**
- Execute loop iterations in parallel using a thread pool (ExecutorService)
- Track completion per-index using a `Set<Integer>` instead of a single integer
- Thread-safe state mutations during concurrent worker execution
- Preserve restartability: on restart, only uncompleted indices execute
- Make thread pool size configurable via workspace config (default 1 = sequential)

**Non-Goals:**
- Non-loop tasks remain sequential — no change
- No change to analysis state schema or content — results identical
- No change to task dependency resolution (DAG scheduler)
- No change to ChatExecutor internals (already uses its own executor for tool calls)

## Decisions

### Decision: Set<Integer> replaces single Integer for index tracking

**Why**: Parallel workers complete in arbitrary order. A single integer cannot represent "indices 2, 5, and 7 done, but 3 and 4 still pending." A HashSet provides O(1) membership checks, minimal serialization overhead via Jackson, and natural idempotency (adding the same index twice is harmless).

**Alternatives considered**:
- BitSet — more memory efficient for dense ranges, but Jackson serialization is non-trivial
- ConcurrentHashMap<Boolean> with indices as keys — over-engineered for the use case

### Decision: Query-based API instead of exposing the set

**Why**: `isIndexSuccessful(task, index)` hides the Set<Integer> behind a boolean query. The main dispatch loop never needs the full set — only needs to check individual indices. This minimizes coupling between AnalyseCmd and TaskState internals.

### Decision: synchronized methods on TaskManager for worker thread safety

**Why**: The only concurrent writes are `markIndexSuccessful` calls from worker threads. `synchronized` on these methods provides mutual exclusion without introducing a separate lock object. `saveState()` is called from within the synchronized block, ensuring state file writes are serialized.

**Alternatives considered**:
- ReentrantLock — more flexibility but unnecessary complexity for two methods
- ConcurrentHashMap for taskStateMap — doesn't help since we mutate the value (replace TaskState record)
- AtomicReference on the set — Jackson serialization doesn't use atomics

### Decision: StateManager uses index-based access instead of iterator

**Why**: The iterator (`loopIterator`) is stateful and single-threaded. In parallel mode, each worker needs to read a specific array element by index. `loopAtIndex(index)` calls `loopPos.get(index)` — direct array indexing. The iterator-based methods (`loopNext`, `canLoop`) are only used by the single-threaded dispatch loop in AnalyseCmd.

### Decision: Thread pool size configurable, default 1

**Why**: Default 1 preserves backward compatibility — existing workspaces work unchanged. Configurability via `config.yml` allows users to tune parallelism per workspace. Not exposed as CLI flag to keep the CLI surface minimal.

## Risks / Trade-offs

- **API rate limits**: Parallel LLM calls may hit provider rate limits. Mitigation: default is 1 (sequential), users opt into higher values aware of their provider limits.
- **Token usage spikes**: Parallel calls consume tokens simultaneously — no change in total tokens, but peak concurrency may affect shared Ollama instances. Mitigation: controllable via loopParallelism config.
- **State file contention**: synchronized saveState serializes file writes. For typical analysis (20-100 modules), contention is negligible — the bottleneck is LLM latency (seconds per call), not file I/O (microseconds).
- **Error handling granularity**: If one index fails, others continue. This matches current behavior. A future enhancement could add fail-fast or retry policies.