# Proposal

## Why

A workspace run costs tokens, and the engine knows how many: every model response carries a token
usage, the chat log of every attempt ends with it, and the live display shows a running total. All of
that is thrown away when the run ends. The only record of what a workspace spent is a set of
per-attempt log files that a reader has to open and sum by hand, and the aggregate in the footer of
the last run says nothing about the workspace as a whole.

The consequence is that a run cannot be costed after the fact. A task that needed three attempts
because its schema kept being violated looks free next to a task that succeeded first time, even
though the first one spent several times as much. A loop task that ran over forty modules has no
per-module breakdown anywhere, so there is no way to see which module was expensive. There is
therefore no answer to the question a user of this engine actually has: how much did this workspace
cost, and where did it go?

The engine already computes the numbers that answer it. The provider reports input, output and total
token counts per response, the executor aggregates them per attempt, and the loop index of the step
is known at every point where they are counted. Storing them costs one field per step in the file
the engine already writes, and it turns the workspace into something that can be costed and compared.

## What Changes

- Record the token usage of every task step in the execution state, so the state of a workspace
  carries what the workspace spent. A step is one non-loop task, or one loop index of a loop task.
- Store the usage of a step as the sum of the attempts of its most recent execution, so a retried
  step reports what it really cost instead of only its last attempt. No history of the individual
  attempts is kept: the figure of a step is replaced when the step runs again.
- Break a loop task's usage down per loop index, so the expensive module of a loop is visible, and
  derive the task's total from those entries so the two cannot disagree.
- Keep the usage of a step that failed, because the tokens of a failed step were spent as well.
- Add a `state tokens` command that reports the token usage of a workspace: a table by default and
  the same data as JSON with a `--json` flag, so the figure can be read by a person and by a script.
- Make the live display show the token usage of the task it belongs to, as the display capability
  already requires. Today the display only adds the usage to the footer total, and the code that
  would put it on the task row is never called, so the requirement and the implementation disagree.
- Remove the token count class that no code uses any more, and the display method that no code
  calls, so the token handling has one implementation and not two.
- No new Maven dependency. The token counts the engine records are the ones the provider reports.

## Capabilities

### New Capabilities

- `workspace-token-usage`: the recording of the token usage of every task step in the execution
  state, the accounting rules that decide which figure a step carries, and the command that reports
  the token usage of a workspace.

### Modified Capabilities

- `live-progress-display`: the requirement that the display shows the input and output token counts
  per task is met by the implementation, so the usage of a task reaches the row of that task and not
  only the footer total.

## Impact

- `src/main/java/com/framstag/llmaj/lc4j/`: the outcome of an attempt carries the token usage of
  that attempt, and the retry driver sums the attempts of a step into the usage of that step.
- `src/main/java/com/framstag/llmaj/tasks/`: the state of a task carries the token usage of the
  task and the usage per loop index, and the task manager records and preserves those figures.
- `src/main/java/com/framstag/llmaj/cli/`: the analysis records the usage of every step it runs,
  and a new `state tokens` subcommand reports it.
- `src/main/java/com/framstag/llmaj/display/`: the token usage of a task reaches its row, and the
  display method that no code called is removed.
- `state.json` gains two optional fields per task entry. Existing state files load unchanged, because
  a field that is absent keeps its default and the engine does not reject fields it does not know.
- `analysis/software-architecture/`: unchanged. The prompts, the schemas and the task list do not
  change; only the engine records what the tasks already cost.
- No Java code, no test and no configuration change is required for the existing behaviour: the
  requirements that are corrected here were already met by the implementation, which is why no
  verification against the running system is needed for them.
