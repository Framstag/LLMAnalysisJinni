# workspace-token-usage Specification

## Purpose

Records what a workspace spends on model calls, step by step, in the execution state the engine
already writes, and reports that spending as a total and a breakdown, so a run can be costed after
the fact instead of being reconstructed from per-attempt log files.

## Requirements

### Requirement: The token usage of a task step is recorded in the execution state

The system SHALL record the input, output and total token count of every task step in the execution
state of the workspace. A step is one non-loop task, or one loop index of a loop task. The counts
SHALL be the ones the model provider reports for that step.

#### Scenario: A non-loop task records its usage

- **WHEN** a non-loop task completes
- **THEN** the execution state entry of that task carries its input, output and total token count

#### Scenario: A loop index records its usage

- **WHEN** a loop index of a loop task completes
- **THEN** the execution state entry of that task carries the input, output and total token count of
  that index under that index

#### Scenario: The recorded usage is the reported usage

- **WHEN** a step is recorded
- **THEN** its recorded counts are the counts the model provider reported for the calls of that step

#### Scenario: A task that was never executed carries no usage

- **WHEN** a task of the analysis has not been executed
- **THEN** its execution state entry carries no token usage

### Requirement: The recorded usage of a step is the sum of the attempts of its last execution

The system SHALL record for a step the sum of the token counts of all attempts of the most recent
execution of that step. It SHALL NOT retain the individual attempts of a step: the figure of a step
SHALL be replaced when the step is executed again.

#### Scenario: A retried step reports what it really cost

- **WHEN** a step was attempted more than once and all its attempts are counted
- **THEN** the recorded usage of that step is the sum of the attempts
- **AND** it is larger than the usage of its last attempt alone

#### Scenario: A step that runs again replaces its figure

- **WHEN** a step is executed again in a later run
- **THEN** the recorded usage of that step is the sum of the attempts of that execution
- **AND** the usage of the earlier execution is not retained

#### Scenario: No per-attempt history is kept

- **WHEN** a step was attempted more than once
- **THEN** the execution state carries one usage for that step
- **AND** it carries no entry per attempt

#### Scenario: A step that was skipped keeps its last figure

- **WHEN** a loop index was already successful and is therefore skipped in a later run
- **THEN** the recorded usage of that index stays the one of its last execution

### Requirement: A loop task's usage is broken down per loop index

The system SHALL record the usage of a loop task per loop index, and SHALL record the usage of the
task itself as the sum of the usages of its indices. The two figures SHALL be written from the same
computation, so they cannot disagree.

#### Scenario: Each index carries its own usage

- **WHEN** a loop task has recorded usage for more than one index
- **THEN** each of those indices carries the usage of its own last execution

#### Scenario: The task total is the sum of the indices

- **WHEN** a loop task has recorded usage for its indices
- **THEN** the usage recorded for the task is the sum of the usages recorded for its indices

#### Scenario: An index without a recorded usage contributes nothing

- **WHEN** a loop task has one index with a recorded usage and one index without
- **THEN** the usage recorded for the task is the usage of the index that has one

### Requirement: The usage of a step that failed is kept

The system SHALL keep the recorded token usage of a step whose attempts were all rejected, because
the tokens of that step were spent.

#### Scenario: A failed task keeps its usage

- **WHEN** a task fails after all its attempts were used
- **THEN** the execution state entry of that task carries the token usage of those attempts

#### Scenario: A failed loop index keeps its usage

- **WHEN** a loop index fails after all its attempts were used
- **THEN** the recorded usage of that index is kept

### Requirement: The token usage of a workspace is reported

The system SHALL report the token usage of a workspace: a total over all tasks, and the usage of
each task, with the usage of each loop index of a loop task. It SHALL report that data as a table
by default and as JSON when a JSON output is requested.

#### Scenario: The report names the workspace total

- **WHEN** the token usage of a workspace is reported
- **THEN** the report states the total input, output and token count over all tasks of that workspace

#### Scenario: The report names every task

- **WHEN** the token usage of a workspace is reported
- **THEN** the report states the usage of each task that has a recorded usage

#### Scenario: The report names the loop indices of a loop task

- **WHEN** the report covers a loop task with recorded usage for more than one index
- **THEN** the report states the usage of each of those indices

#### Scenario: The report is also available as JSON

- **WHEN** the token usage of a workspace is requested as JSON
- **THEN** the report is written as JSON holding the same totals and per-task and per-index usages

#### Scenario: A workspace without recorded usage is reported as such

- **WHEN** the token usage of a workspace is reported and no task of it has a recorded usage
- **THEN** the report states that no token usage is recorded

### Requirement: The recorded usage of a task is removed with the task

The system SHALL remove the recorded token usage of a task when the execution state of that task is
dropped, and SHALL remove every recorded usage when the execution state is cleared.

#### Scenario: Dropping a task removes its usage

- **WHEN** the execution state of a task is dropped
- **THEN** the recorded token usage of that task is no longer reported for that workspace

#### Scenario: Clearing the state removes every usage

- **WHEN** the execution state of a workspace is cleared
- **THEN** no token usage is reported for that workspace
