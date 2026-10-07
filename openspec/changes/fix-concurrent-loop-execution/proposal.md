# Proposal

## Why

A run of `analysis/software-architecture` against Apache Maven (workspace `workspaces/maven`) wrote an
8.9 GB `logs/engine.log` and kept the reserved warning line of the TUI busy with the same two messages
for twelve minutes. 96% of that file - 34,030,904 records - is one message pair repeated:

```text
ERROR [<Task>] c.framstag.llmaj.state.StateManager - Loop already started
ERROR [<Task>] c.framstag.llmaj.cli.AnalyseCmd - Configuration error, aborting task <Task>!
```

distributed over `ModuleSubdirectories` (16,464,428), `FileStatistics` (8,862,865), `ModulePurpose`
(7,060,140) and `ModuleArchitecture` (1,643,471). Three defects combine:

- **The loop cursor is process-global.** `StateManager` holds it in a single field (`loopPos`,
  `StateManager.java:98-121`), so only one loop task can execute at a time. The domain has six loop
  tasks over `/modules/modules` (`ProgrammingLanguages`, `ModuleBuildfileAnalysis`, `ModulePurpose`,
  `ModuleArchitecture`, `ModuleSubdirectories`, `FileStatistics`) and they become runnable together;
  the first one to start makes every other one fail.
- **A rejected start leaves the task PENDING.** `AnalyseCmd.java:378-385` logs
  `Configuration error, aborting task` and returns without marking the task failed.
- **The scheduler re-dispatches it immediately.** The dispatch loop (`AnalyseCmd.java:287-306`)
  re-submits every runnable pending task on each completion, and the runner always offers its task id
  to the completion queue in its `finally` block (`AnalyseCmd.java:546-549`). Submit, abort, offer,
  take, submit: a self-feeding spin that runs at full CPU for as long as another loop task holds the
  cursor, and that repeats until the last loop task finished.

Two amplifiers turn that into the observable damage:

- the engine log file has no size bound (`EngineLogRouting.java:146-152`, a plain `FileAppender` with
  `append=false`), so the spin grows the file without limit;
- every one of those records also reaches the display (`LogLineAppender.java:23` forwards every
  `WARN`/`ERROR`, `ProgressDisplay.java:160-173` repaints on each newer record), which is the wall of
  errors the run showed at the bottom of the TUI.

The analysis did complete, because the loop tasks happened to run one after another. It cost CPU,
wall-clock time and a log file that no run should produce, and it buried the real rejections
(`TechnologyStack` failed, every metric batch was rejected once) in 34M lines of noise.

## What Changes

- **BREAKING (engine internal): the loop cursor belongs to one task execution.** The cursor moves out
  of the shared `StateManager` field into a per-execution value that `AnalyseCmd` obtains for the
  execution and releases when it ends. Loop tasks whose dependencies are satisfied execute
  concurrently, as loop iterations within a task already do. `startLoop`/`endLoop`'s "already
  started" state no longer exists; a missing or non-array `loopOn` target is reported for the task it
  belongs to and does not affect any other task.
- **A task whose execution cannot start is marked failed.** It writes no response property, does not
  unlock dependents, is reported once, and is executed again on the next run.
- **The scheduler needs a state change to dispatch again.** A task that ends without changing its
  status is not dispatched a second time in the same run, so a pending task can no longer keep the run
  alive by reporting itself. The task is reported instead, once, and the run ends when only blocked or
  unstartable tasks remain.
- **The engine log file is bounded.** The engine log keeps the newest records within a fixed size
  bound, so no run can fill the workspace with engine log records. The bound is a constant, not a
  configuration option (see Open Questions).
- **A repeating record does not repaint the TUI warning line.** A record whose text equals the record
  currently shown does not trigger a repaint, so a repeated condition cannot keep the display busy.
- **No change to the domain, its tasks, prompts or schemas**, and no change to the loop parallelism
  semantics of `loopParallelism`.

## Capabilities

### New Capabilities

None. Every behaviour this change defines extends an existing capability.

### Modified Capabilities

- `loop-parallelism`: "Parallel loop task execution" gains the rule that the loop cursor is owned by
  one task execution, so that concurrent loop tasks are possible; a new requirement "The loop cursor
  belongs to one task execution" replaces the single global cursor and defines release, per-task
  reporting of a broken `loopOn` target, and concurrent writes into the same analysis array.
- `parallel-task-execution`: "Thread-safe state mutations" gains the concurrent-loop-task case; a new
  requirement "Scheduler progress requires a state change" states that a task is dispatched at most
  once per run unless a completion changed its status, and that a task which cannot be dispatched is
  reported once instead of being re-submitted.
- `task-failure-handling`: a new requirement "A task that cannot start counts as failed" covers the
  unstartable execution (broken `loopOn` target, rejected configuration), which today stays PENDING.
- `live-progress-display`: "Engine log output is diverted while the TUI owns the terminal" gains the
  size bound of the engine log file; "TUI shows the most recent warning or error" gains the rule that
  an identical record does not repaint the reserved line.

## Impact

Affected code:

- `state/StateManager.java` - the loop cursor leaves the shared state; a factory for a per-execution
  cursor replaces `startLoop`/`endLoop`/`loopAtIndex`/`getLoopArraySize`, and `updateLoopState`
  becomes a write through the cursor under the existing synchronization.
- `cli/AnalyseCmd.java` - the loop task runner obtains and releases its cursor, marks an unstartable
  task failed, and the dispatch loop stops re-submitting a task that did not change its status.
- `logging/EngineLogRouting.java` - the engine log appender becomes size-bounded and keeps the newest
  records.
- `display/ProgressDisplay.java` - the reserved warning line ignores a record that repeats what it
  already shows.
- tests: new tests for two concurrent loop tasks over the same array, for an unstartable task
  (failed, one dispatch, one report), for the dispatch rule, for the log bound, and for the reserved
  line; existing loop/scheduler tests are adjusted where they assert the global cursor.

Affected docs: `AGENTS.md` (loop state and state rules), `README.md` only if a user-visible option is
added (it is not).

Not affected: `analysis/**` (tasks, prompts, schemas, documentation templates), MCP tools,
`state.json` format, `analysis.json` format, the `Config` file format.

Relationship to the other proposal: `fix-response-contract` covers the rejections this run also
produced (the schema description, `TechnologyStack`, the violation report, the SBOM-less path, log
levels of recoverable tool conditions). The two changes touch disjoint files and can be applied in
either order; this one is the blocking defect, because it costs CPU and disk on every run of a domain
with more than one loop task.

## Open Questions

Recorded rather than decided, so design and tasks can settle them:

- Whether the engine log bound should be a `config.json` option as well, or stay a constant.
- Whether the discarded part of the log should be reported to the user (a marker in the file, a line
  on the console, or both).
- Whether `loopParallelism` should be reduced when several loop tasks run concurrently, because the
  number of live worker threads is now the number of concurrent loop tasks times `loopParallelism`.
- Whether a task that cannot be dispatched should end the run with a non-zero exit status.
