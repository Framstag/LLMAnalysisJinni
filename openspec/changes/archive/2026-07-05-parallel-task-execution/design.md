## Context

Current execution flow in `AnalyseCmd.call()`:

```
while (taskManager.hasPendingTasks()) {
    task = taskManager.getNextTask()    // first pending with deps met
    execute(task)                        // sequential, blocks until done
    taskManager.markTaskAsSuccessful()   // adds tags, may unlock dependents
}
```

`TaskManager.calculateNextTask()` scans pending tasks and returns the **first** whose `dependsOn` tags are all in `scheduledTags`. It returns exactly one task per call. This is inherently sequential — even when 5 tasks are runnable, they execute one at a time.

The existing loop parallelism (`task.hasLoopOn()` branch) already demonstrates thread-safe patterns: `synchronized(stateManager)` blocks, deep-copy snapshots for template rendering, and per-worker thread pools. This change reuses those patterns at the DAG level.

```
BEFORE:

Scheduler              Worker
────────               ──────
getNextTask() ──────▶  task-A
                       markDone()
getNextTask() ──────▶  task-B
                       markDone()
getNextTask() ──────▶  task-C
                       ...

AFTER:

Scheduler              Pool-1              Pool-2 (per loop task)
────────               ──────              ─────────────────────
getRunnable() ──────▶  task-A ──────▶
              ──────▶  task-C ──────▶
              ──────▶  task-D ──────▶
                       ...
wait for one ◀──────── task-A done
getRunnable() ──────▶  task-B (unlocked by A)
                       ...
```

## Goals / Non-Goals

**Goals:**
- Execute independent DAG tasks in parallel
- Honour all `dependsOn` tag dependencies — never run a task before its deps
- Thread-safe state mutations (TaskManager, StateManager)
- MDC logging for attributable execution-trace output
- `--single-step` works: one task + its loops, then stop
- Configurable parallelism via `--task-parallelism` CLI flag and `config.json`

**Non-Goals:**
- Changing the task definition YAML format
- Changing the loop-parallelism mechanism (per-task pools stay)
- Dynamic task prioritization (FIFO within runnable set is fine)
- Cancellation of running tasks
- Retry logic for failed tasks

## Decisions

### Decision 1: Scheduler thread + BlockingQueue completion channel

A single scheduler thread (the main `call()` thread) runs the dispatch loop. It submits runnable tasks to a shared worker pool. Workers push their task ID to a `BlockingQueue` on completion. The scheduler blocks on `queue.take()`, then checks for newly-unblocked tasks.

```
Scheduler Thread                 Worker Pool
────────────────                 ───────────
                                 
loop:                            
  runnable = getRunnableTasks()  
  for t in runnable:             
    submit(t) ──────────────────▶ worker runs task
                                 worker pushes id to doneQueue
  id = doneQueue.take() ◀────────
  // task completed, check for newly runnable tasks
```

**Alternatives considered:**
- **CompletableFuture chaining** — Create a `CompletableFuture` per task, chain dependents with `thenCompose()`. More elegant but requires building the entire future DAG upfront. Doesn't handle the dynamic `executeOnly` filter well.
- **Reactive streams** — Overkill for a CLI tool. No backpressure needed.
- **Continuation-passing** — Each task callback submits its dependents. Tightly couples task execution with scheduling logic.

### Decision 2: Two-tier thread pools (no sharing)

DAG tasks run in a fixed pool sized by `taskParallelism`. Loop tasks create their own temporary pool sized by `loopParallelism` (as today). No thread sharing between tiers.

**Why not a shared pool?** Deadlock risk. A loop task's DAG thread blocks on `allOf().join()` waiting for loop workers. If loop workers compete with other DAG tasks for the same pool threads, the loop task may never get enough workers to complete — classic thread starvation deadlock.

```
DAG Pool (size = taskParallelism)
┌──────┬──────┬──────┬──────┐
│ T1   │ T2   │ T3   │ T4   │   ← DAG task threads
│ modA  │ modB  │ idle  │ idle │     (block on allOf for loops)
└──────┴──────┴──────┴──────┘

Loop Pool A (size = loopParallelism, created per loop task)
┌──────┬──────┬──────┬──────┐
│ A-0  │ A-1  │ A-2  │ A-3  │   ← loop workers
└──────┴──────┴──────┴──────┘
```

**Worst-case thread count**: `taskParallelism × (1 + loopParallelism)`. With defaults (2 × 5 = 10) — negligible.

### Decision 3: Synchronized state mutations

`TaskManager.markTaskAsSuccessful()` and `StateManager.updateState()` become `synchronized` methods. This is the minimal change needed for thread safety — no lock objects, no `ReentrantReadWriteLock`, no lock-free data structures.

**Why not finer-grained locking?** The critical sections are fast (set remove/add, JSON tree set). LLM calls dominate runtime by orders of magnitude. Lock contention is negligible.

**Why not lock-free?** Jackson's `ObjectNode` is not thread-safe. Wrapping it with concurrent data structures would require deep copies or a complete rewrite of the state model. Not worth it.

### Decision 4: MDC for log attribution

SLF4J's `MDC` (Mapped Diagnostic Context) tags every log line from a thread with task context. Set before execution, cleared after.

```java
// Before task execution:
MDC.put("taskId", task.getId());
if (loopIndex != null) MDC.put("loopIndex", String.valueOf(loopIndex));

// In logback.xml:
// <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level [%X{taskId}:%X{loopIndex}] %logger{36} - %msg%n</pattern>

// After task execution:
MDC.clear();
```

**Why MDC over message-level changes?** Zero changes to `ChatExecutor` or any logging call site. Every log line — including chat messages, tool calls, errors — automatically carries task context. Works with existing `--execution-trace` mode.

### Decision 5: `--single-step` executes one task

In parallel mode, `--single-step` submits the first runnable task, waits for it (including all its loop indices), then stops. No ambiguity about "what is one step."

### Decision 6: Configurable via CLI and config.json

New `--task-parallelism` CLI option (default 2). Also settable in `config.json` as `taskParallelism`. The `Config` class gains the field with getter/setter.

## Risks / Trade-offs

**[Thread count explosion]** → Worst case `taskParallelism × (1 + loopParallelism)` threads. With defaults: 10 threads. With aggressive settings (8 × 8): 72 threads. Acceptable for a CLI tool. Mitigation: document that `loopParallelism` should be reduced when `taskParallelism` is high.

**[State file write contention]** → Multiple workers calling `saveState()` concurrently. Already `synchronized` on `StateManager`, so writes are serialized. Each write serializes the entire `analysisState` to disk. For N parallel tasks completing near-simultaneously, you get N sequential disk writes. Acceptable — LLM calls take seconds, disk writes take milliseconds.

**[MDC context leakage]** → If a worker thread is reused by the pool and MDC is not cleared, stale context leaks into unrelated log lines. Mitigation: always call `MDC.clear()` in a `finally` block after task execution.

**[Dependency deadlock in DAG]** → If the DAG has a cycle, `validateNoDependencyCycles()` catches it at init time. If all runnable tasks complete but pending tasks remain (shouldn't happen with a valid DAG), the scheduler detects this and logs an error.

## Open Questions

- **Task-level progress in TUI**: Currently the TUI shows per-worker interaction for loop tasks. For parallel DAG tasks, should we show per-task interaction timelines too? The TUI already supports this (non-loop tasks show `[→ ← ◆ ✓]` timeline). No changes needed.
- **Failure handling**: If one task in a parallel batch fails, should its dependents still run? Current behavior: failed tasks don't add their tags to `scheduledTags`, so dependents remain blocked. This is preserved.
