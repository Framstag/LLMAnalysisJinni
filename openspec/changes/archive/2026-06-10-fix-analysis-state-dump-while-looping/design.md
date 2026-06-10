## Context

`AnalyseCmd.java` orchestrates LLM task execution with two paths:

1. **Non-looping tasks** (lines 236-270): After LLM responds, `stateManager.updateState()` + `stateManager.saveState()` persist results to `analysis.json`
2. **Looping tasks** (lines 154-234): For each loop index, `stateManager.updateLoopState()` stores the result in the in-memory `ObjectNode` (`analysisState`). But `saveState()` is never called — data exists only in the JVM heap.

The `loopPos` reference obtained from `analysisState.at(loopOn)` points directly into the mutable `ObjectNode` tree, so in-memory updates do affect the live object. The gap is purely in persistence: the updated tree is never written to `analysis.json`.

## Goals / Non-Goals

**Goals:**
- Persist loop task results to `analysis.json` so they survive process restarts
- Maintain correctness with parallel loop execution (thread-safe regarding save timing)
- Keep change minimal — small synchronized block addition in the orchestration layer

**Non-Goals:**
- Allowing in-flight data loss — every completed index MUST be on disk before next starts
- Not refactoring StateManager or TaskManager APIs
- Not changing state.json behavior (task index tracking is already correct)

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
|| Where to call `saveState()` | Inside worker thread, after each loop index completes | `analysis.json` must be up-to-date after every index. If process crashes mid-loop, only that index is lost — not all subsequent |
|| Save granularity | Per index | Each LLM response independently valuable. Losing N-1 results on crash is unacceptable |
|| Thread safety | `synchronized(stateManager)` block wrapping `updateLoopState` + `saveState` | `saveState()` already `synchronized` on StateManager instance. External `synchronized(stateManager)` uses same monitor — makes update+save atomic across parallel threads. Jackson `ObjectNode` not thread-safe; monitor serializes all mutations |

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
|| `synchronized` block serializes parallel thread writes, reducing throughput | Acceptable — `saveState()` writes entire `analysisState` tree (~10-100KB JSON). I/O is fast; serialization cost dwarfed by LLM call time (seconds per index) |
|| Thread holding lock crashes mid-write → corrupted `analysis.json` | `markIndexSuccessful` called AFTER the synchronized block. If crash during save, index stays unmarked and retries next run. Jackson `writeValue` filesystem-atomic for small files |

## Migration Plan

1. In loop worker (lines 209-218 AnalyseCmd.java), wrap `updateLoopState` + `saveState` in `synchronized(stateManager)` block
2. Remove redundant post-loop `saveState()` from endLoop area (no longer needed)
3. Build & test with `mvn verify -DskipTests`
4. Run end-to-end analysis on a project with loop tasks, verify `analysis.json` contains findings per completed index

Rollback: revert the synchronized block addition.

## Open Questions

*None.*