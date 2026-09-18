## Context

See proposal.md - Why. Two contracts constrain the fix:

- `ChatExecutor.executeMessages` documents its contract in its own javadoc: it returns "a JSON structure following the schema or null". A missing payload is therefore an ordinary return value, not an error signal, and callers must handle it.
- `TaskManager` already implements the semantics that failure implies: `markTaskAsSuccessful` adds the task's tags to the scheduled tags and removes the task from the pending set, while `markTaskAsFailed` does neither and stores `FAILED`. When state is loaded again, only `SUCCESSFUL` tasks are removed from the pending set and only their tags are scheduled.

So the dependents-and-retry behaviour needs no new mechanism. The defect is a call site that reports one outcome to the user and records another.

## Goals / Non-Goals

**Goals:**

- One outcome per task execution, consistently reflected in the task-manager state, `state.json`, and the display.
- The non-loop and loop paths agree about what counts as a failed execution.
- No change to the task state model, to `TaskManager`, or to the loop path.

**Non-Goals:**

- Introducing a new task state. The model is `PENDING` / `PROCESSING` / `SUCCESSFUL` / `FAILED` and is documented in `AGENTS.md`.
- Making a schema violation fail a task. Validation stays warn-only by design and is now pinned by a requirement in this change.
- Reclassifying a literal JSON `null` payload as a missing payload. It parses as a JSON node today and is accepted; treating it as missing is a separate decision.
- Improving the dispatcher's "possible dependency deadlock" message when a failed task legitimately blocks its dependents.

## Decisions

### Decision 1: Derive the task status from the response, mirroring the loop path

The non-loop path marks the task successful when a payload was returned and failed when there was none, using the same condition that already selects between `onTaskComplete` and `onTaskError`.

Alternatives considered:

- *Keep marking successful and only log the error.* Rejected: this is the defect. It publishes tags whose response was never written, and it hides the task from the next run.
- *Add a distinct state such as `INCOMPLETE`.* Rejected: the state model is fixed and documented, `FAILED` already carries the right meaning ("retry me"), and a new state would ripple through `state dump`, `state drop`, the display, and the specs.

### Decision 2: Handle the missing payload at the call site, not inside `ChatExecutor`

`ChatExecutor` keeps its documented null return. The decision belongs where both the task definition and the task manager are available, which is the caller.

Alternative considered: throw a dedicated exception from `ChatExecutor` when there is no payload, and let both paths fail through the existing exception handling. This would remove the asymmetry at the root, but it changes a documented contract used by both execution paths and turns a normal LLM outcome into an exception; rejected as a larger blast radius than the defect warrants.

### Decision 3: Pin the outcome rules in a spec, including the non-failure of schema violations

The capability spec states both what counts as failure and what deliberately does not, so that the next person to look at a warning about a schema violation does not "fix" it into a task failure.

## Risks / Trade-offs

- [A misbehaving model now stops a whole branch of the DAG, so a run may complete fewer tasks than before] → Intended: the branch could not have produced valid results. The failed task is reported per task, and re-running retries it once the model behaves.
- [A blocked branch leaves pending tasks that can never run, which trips the dispatcher's "possible dependency deadlock" message] → The message already says "possible", and the failed task that caused it is reported. Improving that message is deliberately out of scope and recorded as an open question.
- [A domain could contain a task that legitimately returns no payload, which would now block its dependents permanently] → Every task declares a `responseFormat` and is expected to return JSON, so such a task is misconfigured and should be marked `active: false`. The failure is visible rather than silent, which is the point of the change.
- [The loop path could drift away from the non-loop path again] → The spec states the loop-and-non-loop agreement as a requirement with a scenario per path, so a future change to one path is checked against the other.

## Migration Plan

- No data migration. Existing `state.json` files keep working; tasks recorded as `SUCCESSFUL` without a response stay as they are until they are dropped with `state drop` or cleared.
- Deploy: single commit containing the outcome handling and the new spec.
- Rollback: revert the commit; the previous behaviour of marking every non-loop task successful returns.
- Verification: run an analysis against a stub model server that answers with an empty payload, and confirm the task is recorded as failed, its dependent is not executed, and the task is retried on the following run. Compare against the same scenario before the fix, where the dependent runs.

## Open Questions

- Whether the dispatcher should distinguish "a failed task blocked this branch" from "the DAG is deadlocked" when it reports remaining pending tasks. Deferrable: it changes a log message, not task outcomes, the approach, or the task breakdown.
