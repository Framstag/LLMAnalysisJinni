## Why

A non-loop task is marked successful no matter how it ends. When the model returns no JSON payload, `ChatExecutor.executeMessages` returns `null`, `AnalyseCmd` logs `No response from chat model` and tells the display the task failed - and then calls `taskManager.markTaskAsSuccessful(task)` anyway.

Two things follow from that:

1. The task's tags unlock its dependents. Those dependents run with a response property that was never written to `analysis.json`, so they analyse missing input while reporting success themselves.
2. `state.json` records the task as `SUCCESSFUL`, so the next run skips the task that never produced a result. `state clear` or `state drop` is needed before the task is attempted again.

The loop path does not have this defect: it collects a failure flag per index and calls `markTaskAsFailed` when any index failed. So the two execution paths disagree about the same condition, and the loop path's behaviour is the one that matches the documented state model, where a failed task is retried.

## What Changes

- **A task whose response contains no JSON payload is marked failed** in the non-loop path, matching what the loop path already does.
- **Its tags no longer unlock dependents**, so dependents that need its response are not started.
- **The task is retried on the next run** instead of being skipped as successful.
- **Display, task-manager state, and `state.json` agree** on the outcome; today the display reports failure while the state says successful.
- **New capability `task-failure-handling`** pins the outcome semantics, including the deliberate rule that a schema violation is not a failure.

Non-goals: schema violations stay warn-only and keep the response (deliberate, documented in `llm-response-schema-validation`); a literal JSON `null` payload is not treated as a missing payload; the loop path's per-index retry behaviour is unchanged; nothing changes about how the dispatcher terminates.

## Capabilities

### New Capabilities
- `task-failure-handling`: what counts as a failed task execution, and what failure implies for dependents, for the recorded task state, and for the next run.

### Modified Capabilities
- *(none - `llm-response-schema-validation` explicitly excludes retry behaviour and task status tracking, and `parallel-task-execution` covers scheduling, parallelism and `--single-step`, not task outcome)*

## Impact

- **Modified files**: `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java` (non-loop outcome handling).
- **Tests**: `src/test/java/com/framstag/llmaj/tasks/TaskManagerTest.java` gains coverage for the failure semantics the fix relies on; the fix itself is verified against a stub model server that returns no payload.
- **Behaviour**: a run in which a task returns no payload now stops that branch of the DAG, reports the task as failed, and retries it next time, instead of continuing with missing analysis input. Runs whose tasks all return payloads are unaffected.
- **No API, dependency, configuration format, or spec-breaking changes.**
