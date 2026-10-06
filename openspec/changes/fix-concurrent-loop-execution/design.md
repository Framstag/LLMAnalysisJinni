# Design

## Context

See `proposal.md` for the run that exposed the defects. The constraints that shape the approach:

- `StateManager` is one instance shared by every task runner in a run (`AnalyseCmd.java:193`). It owns
  the analysis state JSON, the synchronization of every state mutation, and - today - the single loop
  cursor field `loopPos`.
- Loop tasks are ordinary DAG tasks. `AnalyseCmd.buildTaskRunner` sees `task.hasLoopOn()` and runs the
  loop body itself, with a per-task `ExecutorService` sized by `loopParallelism`
  (`AnalyseCmd.java:396-475`). The DAG pool (`taskParallelism`) therefore bounds how many loop tasks
  run at once; the loop pool is per task, not shared.
- Every loop worker deep-copies the analysis state for its prompt (`AnalyseCmd.java:415-417`) and
  writes its result back through `stateManager.updateLoopState(...)` plus `saveState()`
  (`AnalyseCmd.java:446-449`). Two loop tasks write *different* response properties of the *same*
  analysis array entry, so the write path is already the concurrency-sensitive one.
- The dispatch loop treats "pending" and "runnable" as independent of "in flight"
  (`AnalyseCmd.java:287-306`), and the runner's `finally` block always reports completion
  (`AnalyseCmd.java:546-549`), which is what makes a non-status-changing return self-feeding.
- The engine log file is a logback `FileAppender` created by the engine itself
  (`EngineLogRouting.java:130-160`); `logback.xml` only defines the console appender.
- `ProgressDisplay` keeps exactly one record as the reserved line and renders it with a debounce
  (`ProgressDisplay.java:160-173`).

## Goals / Non-Goals

**Goals:**

- Loop tasks that are runnable in the same DAG run execute concurrently, without a shared cursor.
- A task execution that cannot start leaves a definite task status, never PENDING.
- The dispatch loop cannot spin: a dispatch requires a completion that changed a task's status.
- The engine log file cannot grow without bound, whatever the code logs.
- The reserved TUI line cannot be repainted by a record that repeats what it already shows.

**Non-Goals:**

- No change to `loopParallelism`, `taskParallelism`, the two-tier pool structure or the DAG.
- No new user-visible configuration for the log bound.
- No change to the analysis domain, the prompts, the schemas, `analysis.json` or `state.json`.
- No change to how a loop index is retried, skipped or recorded.
- No general log rate-limiting layer; the repeated-record case is handled where the record is shown.

## Decisions

### Decision 1: The loop cursor becomes a per-execution value

A cursor value holds the loop target array and the index it belongs to, and is created from the
analysis state by a `StateManager` factory for exactly one loop task execution. The loop runner passes
that cursor to its workers instead of relying on shared state, and drops it when the execution ends.
Writes still go through `StateManager.updateLoopState(cursor, index, property, value)`, so the monitor
that serializes state mutation stays exactly one lock.

Alternatives considered:

- **Keep `loopPos` and guard it by task id in a map.** Rejected: it keeps the cursor in the shared
  object that outlives the execution, so "Loop already started" becomes "loop for task X already
  started", and the release path stays implicit. The bug is the ownership, not the field count.
- **Serialize loop tasks with a semaphore or a queue in front of the loop body.** Rejected: it makes
  the collision invisible instead of fixing it, keeps the wait as long as the slowest loop task, and
  hides a second effect - the run currently cannot report *which* loop tasks were prevented.
- **Give each loop task its own `StateManager` over the same state file.** Rejected: the state writers
  would clobber each other's `analysis.json` and `state.json` writes, and the state file is written
  per accepted index.

### Decision 2: An unstartable execution marks its task failed

If the loop target does not exist, is not an array, or the task configuration is rejected, the runner
marks the task failed through `TaskManager.markTaskAsFailed`, reports the reason for that task, and
writes nothing. This is the same outcome as an exhausted attempt budget: the task is not successful,
its tags do not unlock dependents, and the next run executes it again.

Alternative considered: keep the return without a status change, and rely on Decision 3 alone.
Rejected: the task would stay PENDING for the rest of the run, `state dump` would show it as pending,
and the reason would exist only as a log record.

### Decision 3: A dispatch requires a completion that changed a task's status

The runner reports completion exactly once, as it does today, but the dispatch loop only treats a task
as dispatchable when it is pending *and* it has not been dispatched in this run with the same status.
A task that returned without changing its status is recorded as rejected for the run, reported once,
and skipped by later dispatch rounds; the run ends when the remaining pending tasks have unsatisfied
dependencies, and those are named.

This is defence in depth: after Decision 2 no runner path returns without a status change, and the
guard keeps a future early return from turning into a spin.

Alternatives considered:

- **Mark every returned task successful or failed, and trust that.** Rejected: the scheduler then
  depends on every current and future return path being complete; the spin cost a 8.9 GB log, so the
  guard is cheap insurance and is testable on its own.
- **Bound the dispatch loop by an iteration counter or a timeout.** Rejected: it hides a scheduling
  defect behind a number instead of naming the task that could not be dispatched.

### Decision 4: The engine log file is bounded by a constant

The engine log appender becomes a rolling appender with a maximum file size and a bound on the total
size of the engine log files in the workspace, keeping the newest records. The bound is a constant in
`EngineLogRouting`, applied whenever the TUI owns the terminal, and needs no configuration. The run
keeps its promise of "the records of the last run are in `<workspace>/logs/engine.log`" for a run
within the bound; older records move to the rolled file names. The engine log files of an earlier run
are removed when the routing is installed, so a workspace always shows the engine log of the run that
is being watched, and a bound that an earlier run reached cannot misreport a later one.

logback checks the size of the file at most once per interval (`checkIncrement`, a rate limit that
protects against a `stat` per record) and that interval is set to zero here: a burst is exactly the
case the bound exists for, and with the default interval a burst would roll nothing at all and write
one file far beyond its bound. With a check per record the overshoot is at most the record that
crosses the bound.

Alternative considered: leave the file unbounded and rely on the fixes above. Rejected: a single
misbehaving component must not be able to fill the disk of a user who leaves a run unattended; the
bound is the only guarantee that stays true even when a new defect is introduced.

### Decision 5: The reserved warning line ignores an identical record

`ProgressDisplay.onLogLine` compares the incoming record with the record it currently shows and skips
the repaint when the text is equal. The record still replaces the shown line (it is the most recent
record, and the requirement is about *what* is shown), only the repaint is skipped. Combined with the
existing debounce this keeps a repeated condition from painting frames at log rate.

Alternative considered: drop repeated records in `LogLineAppender` before they reach the display.
Rejected: the display is not the only consumer, and dropping records in the logging path would hide
them from any future consumer; the display is where "do not paint the same thing again" belongs.

## Risks / Trade-offs

- **More live threads.** Concurrent loop tasks multiply worker threads: `n` loop tasks run at once,
  each with `loopParallelism` workers. The DAG pool still bounds how many loop tasks are started, and
  `loopParallelism: 1` restores one worker per loop task. Recorded as an Open Question rather than
  changing a default.
- **Higher peak memory.** Each loop worker holds a deep copy of the analysis state for its prompt.
  Concurrent loop tasks therefore multiply that copy. The copies are per worker today; the change
  multiplies the number of workers that exist at the same time.
- **More concurrent state writes.** Two loop tasks now call `updateLoopState`/`saveState` on the same
  array. Both are serialized by the existing monitor, and the write path is per property, so no update
  is lost; the risk is a longer lock hold on a large `analysis.json`, which is bounded by the array
  size, not by the number of tasks.
- **A failing loop task can now start in parallel with a healthy one.** Its failure is reported on its
  own row, which is the intended behaviour, but a run of a domain with six loop tasks has six chances
  to fail at once. The status rules of `task-failure-handling` are unchanged.
- **Rolled engine log files are new files in `logs/`.** A workspace gains one or two extra files, with
  a stable name pattern, and `logs/engine.log` keeps meaning "the newest engine records".
- **An identical record is not repainted, but the display keeps painting for other reasons.** The
  reserved line skips a record that repeats what it shows; the render timer still paints when the frame
  changes for another reason (the elapsed time). The repeated-record rule is therefore a check at the
  repaint decision, not a limit on the number of frames a run may paint.

## Migration Plan

- No configuration, state or domain migration: the loop cursor is an internal value, and the log bound
  is a constant.
- An affected workspace keeps the `analysis.json` it has. Re-running the analysis re-executes only the
  tasks that are not successful; the loop indices that succeeded in an earlier run stay skipped.
- Rollback requires reverting the code; there is no configuration switch, because none of these are
  behaviours a user would want back.

## Open Questions

- Whether the log bound should be configurable, and which value is useful for a run that is debugged
  after it failed (a bound that is too small loses the diagnosis, one that is too large loses the
  guarantee).
- Whether the run should report that it discarded older engine log records, and where.
- Whether `loopParallelism` needs a note in the README now that more than one loop task can use it at
  the same time.
- Whether a run in which a task could not be dispatched should exit non-zero, since the DAG is
  incomplete in a way the user did not ask for.
