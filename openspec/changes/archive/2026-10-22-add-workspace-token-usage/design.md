# Design

## Context

The engine already knows what every model call costs, and the numbers are computed at the point where
the response arrives. What is missing is a place to put them.

```
TOKEN FLOW TODAY
================
  ChatExecutor.executeMessages()
     chatResponse.metadata().tokenUsage()        [per call, nullable counts]
        |
        +--> callback.onTokenUsage(taskId, loopIndex, aggregate)
        |       +--> ProgressDisplay  --> footer aggregate only
        |       +--> SimpleOutput     --> footer aggregate only
        |
     aggregateTokenUsage = TokenUsage.sum(...)   [per attempt]
        +--> writeChatLog --> logs/<task>[_<idx>][.attemptN].log
                               "-- Token usage: IN .. / OUT .. / TOTAL .."
```

Three facts shape the approach:

- The usage is available **per call**, is aggregated **per attempt**, and the loop index is known at
  every point where it is counted. The unit the state file must record is the **step**, not the call
  and not the attempt.
- `TokenUsage` carries `Integer` counts, not `int`. A provider may report no input count. Every
  consumer of the counts null-checks them, and the recorded figure has to survive a provider that
  reports nothing.
- The state file is written with the JSON mapper and read with the YAML mapper. YAML is a superset
  of JSON, so the file round-trips today, and a new optional field is read as absent when it is
  missing.

The state entry of a task is a whole-value replacement at four places, so every field that is not
part of the replacement has to be carried over by hand:

```
TaskManager call sites that build a new TaskState
  markIndexSuccessful   -> replaces the entry, keeps successfulIndices
  markTaskAsSuccessful  -> replaces the entry, drops successfulIndices
  markTaskAsFailed      -> replaces the entry, keeps successfulIndices
```

`markTaskAsSuccessful` deliberately drops the successful indices, because a finished task no longer
needs them. The token usage is different: it is not progress, it is a cost, and it must survive the
same replacement.

## Goals / Non-Goals

**Goals:**

- One recorded figure per step, replaced when the step runs again.
- The figure of a step is what the step really cost, retries included.
- A loop task can be broken down per index, and its total is derived from that breakdown.
- The recorded usage is read back by a command, as a table and as JSON.
- The change is additive: a state file written before the change still loads.

**Non-Goals:**

- No cost in money. The engine has no price table and this change does not add one.
- No history. The figure of an earlier execution of a step is gone once the step runs again.
- No change to the analysis domain, the prompts, the schemas or `analysis.json`.
- No change to the meaning of the footer total of the live display. That total is the usage of the
  current run; the recorded usage is the lifetime of the step. They differ on purpose.

## Decisions

### A step's figure is the sum of the attempts of its last execution

**Decision.** The recorded usage of a step is the sum of the token counts of all attempts of the most
recent execution of that step.

**Rationale.** The alternative, keeping only the last attempt, under-reports every retried step by
the amount the earlier attempts cost. In the `maven` workspace, for example, one task spent 15490
tokens on its first attempt and 15025 on its second; keeping the last attempt alone reports 15025
for a step that spent 30515.

**Alternatives considered.**

- *Last attempt only.* Cheapest, and a literal reading of "the last call for each task". Rejected,
  because the number would not be the spend.
- *Accumulate across runs.* Every run would add to the previous total, so the figure would grow
  without bound and would no longer describe one step. Rejected.

**Consequence.** The recorded figure of a step is replaced by its next execution, and the record of
the earlier execution is not kept. A step that is skipped in a later run keeps the figure of its last
execution, because it spent nothing new.

### The unit of the record is the step, and the step is where the attempts are summed

**Decision.** The token usage of an attempt travels with the outcome of that attempt, and the retry
driver sums the attempts of a step.

**Rationale.** The outcome of an attempt is already the unit the retry driver works with: it holds
either a payload or the reason the attempt was rejected. Carrying the usage with it means the sum
happens in the one place that knows the attempt boundary, and no caller has to know how many
attempts a step took.

```
  ChatExecutor  --(usage of the attempt)-->  TaskStepOutcome
                                                    |
  TaskStepRetrier  --(sum over the attempts)-->  one usage per step
                                                    |
                            +-----------------------+-----------------------+
                            |                                               |
                     non-loop task                                    loop task, index N
                            |                                               |
                     task-level usage                               index-level usage
                            |                                               |
                            +----------------> TaskManager --> state.json
```

**Alternatives considered.**

- *Sum in the caller.* The caller would have to know the attempt count and add up the attempts
  itself, which spreads the rule over the two places that run steps. Rejected.
- *Have the display accumulate.* The display already sees every usage, but it is a display concern,
  it is not the owner of the state, and it is not present in every run. Rejected.

### The state entry of a task carries a task figure and a per-index figure

**Decision.** The state entry of a task carries the usage of the task and, for a loop task, the usage
of each of its indices. The usage of a loop task is the sum of the usages of its indices, and both are
written from the same computation.

**Rationale.** The two figures answer two different questions. "What did this task cost?" is answered
by the task figure, which a reader must not have to compute. "Which module of the loop was
expensive?" is answered by the index figures. Writing both from one computation means they cannot
drift apart.

**Alternatives considered.**

- *Per-index only.* One source of truth, but every reader of the task total has to sum the indices,
  and a non-loop task would need the same field with a different meaning.
- *Per-index for every task.* A non-loop task would have to be a task with the index zero, which
  confuses "loop index" with "step". Rejected.

### The fields are optional, and an absent field means "not executed"

**Decision.** A task entry carries the usage only once the task has been executed. A task that has
never run carries no usage, and a task that ran and reported nothing carries no usage.

**Rationale.** The counts are nullable at the source. A provider that reports no input count must not
turn into a recorded zero, because a zero is a number and a missing number is not. The state file
therefore keeps the figure absent until the step produced one, and a reader treats an absent figure as
"nothing is known", not as "it was free".

**Consequence.** Existing state files load unchanged, because a field that is absent keeps its
default and the mapper does not reject fields it does not know.

### The usage of a failed step is kept

**Decision.** A step whose attempts were all rejected keeps the usage of those attempts.

**Rationale.** The tokens were spent. A step that needed three attempts because its schema kept
being violated is exactly the step whose cost a reader wants to see, and it is exactly the step whose
usage would be lost if only successful steps were recorded.

**Consequence.** A failed task carries a usage, and a failed loop index keeps its usage even though
the index is not in the set of successful indices. The two facts are independent: an index can be
failed and still have cost money.

### The report is a new command, and the state command is where it belongs

**Decision.** The usage of a workspace is reported by a new `state tokens` subcommand, which prints a
table by default and JSON when asked for it.

**Rationale.** The state command group is the group that owns the state file, and the report is a
reading of the state file. The existing `state dump` prints a status line per task through the
logging framework, which is readable but not machine-readable, and the report has to be readable by a
script as well.

**Alternatives considered.**

- *Extend `state dump`.* Fewer commands, but it mixes the status of a task with its cost and changes
  the output of an existing command.
- *Put the usage in the footer of the live display.* The footer already shows a total, but it is the
  total of the current run and disappears when the run ends, which is the problem being solved here.

### The per-task token row is made to work, not removed

**Decision.** The display puts the usage of a task on the row of that task, which is what the display
capability already requires. The method that does it exists but is never called, so the call is
missing rather than the feature.

**Rationale.** The requirement is right and the code was written for it; only the wiring is absent.
Making the code match the requirement is cheaper than amending the requirement, and the row is the
place a reader looks for the cost of one task.

**Alternatives considered.**

- *Weaken the requirement to the footer total.* This would have been a smaller change, but it would
  remove a feature the capability advertises and that the code already has half of.

### The dead token count class is removed

**Decision.** The token count class in the task package is deleted, and the display method that no
code calls is either called or removed with it.

**Rationale.** The class has had no user since the configuration was moved into the config class, and
the display method has no caller. Two implementations of the same idea in one codebase is how the two
drift apart in the first place.

## Risks / Trade-offs

- **A provider reports no counts** → the usage of that step is not recorded, and the figure of a
  step whose provider reported nothing stays absent. The report states what is recorded, and does not
  present an absent figure as zero. Mitigation: the counts are null-checked at every point where they
  are read, and the report distinguishes "no usage recorded" from "zero tokens".

- **The recorded total of a loop task and the sum of its indices can disagree** if they are written
  at two different moments → both are written by one computation at one moment, from the same set of
  index figures. Mitigation: one write path, one test that asserts the two agree.

- **A loop task that fails after some indices succeeded** → the indices that succeeded keep their
  usage, the failed index keeps the usage of its attempts, and the task figure is the sum over all
  of them. The task figure is therefore the cost of the whole attempt, not the cost of the part that
  worked. This is intended, but it means the task figure and the set of successful indices are not
  in a one-to-one relation.

- **The state file grows** → one usage per step. A workspace with many loop indices holds one small
  entry per index. The file stays small in comparison to the analysis results.

- **The recorded usage and the footer total of a run disagree** → the footer total is the usage of the
  current run, the recorded usage is the usage of the last execution of each step. A workspace that
  was run twice shows the last execution, not the sum of both runs. Mitigation: the report states
  that a figure is the usage of the last execution of the step.

- **The engine writes the state file from several threads** → the task manager methods that record
  the usage are synchronized like the ones that already write the state, so a concurrent loop worker
  cannot lose the figure of another worker.

## Migration Plan

None needed. The new fields are optional, the mapper does not reject unknown fields, and a state file
written before the change loads unchanged: the tasks of such a file carry no usage, and the report
states that no usage is recorded for them. A workspace that is re-analysed after the change fills the
figures in as its tasks run.

## Open Questions

None. The decisions that shape the approach — the accounting rule, the record shape, the report
command, the report format, the fate of the usage on `state drop` and `state clear`, and the
direction of the display fix — are settled, and the specs follow them.
