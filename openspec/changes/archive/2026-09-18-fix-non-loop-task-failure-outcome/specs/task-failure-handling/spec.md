## Purpose

Defines when a task execution counts as failed, and what that failure implies for the tasks that depend on it, for the recorded execution state, and for the next run.

## ADDED Requirements

### Requirement: A task whose response carries no JSON payload is marked failed

When a task's model response contains no JSON payload, the task SHALL be marked failed rather than successful, and its response property SHALL NOT be written to the analysis results.

#### Scenario: Non-loop task returns no payload
- **WHEN** a non-loop task's model response contains no JSON payload
- **THEN** the task SHALL be marked failed
- **AND** no response property SHALL be written for that task
- **AND** the failure SHALL be reported for that task

#### Scenario: Loop task has an index without payload
- **WHEN** a loop task has at least one index whose model response contains no JSON payload
- **THEN** the task SHALL be marked failed

#### Scenario: Task returns a payload
- **WHEN** a task's model response contains a JSON payload
- **THEN** the task SHALL be marked successful
- **AND** the payload SHALL be written to the response property of that task

### Requirement: A failed task does not unlock its dependents

A task marked failed SHALL NOT make its tags available to the dependency check, so tasks that depend on those tags SHALL NOT be executed.

#### Scenario: Dependent is not started
- **WHEN** a task fails
- **AND** another task declares a dependency on a tag that only the failed task would have produced
- **THEN** the dependent task SHALL NOT be executed in that run

#### Scenario: Independent tasks are unaffected
- **WHEN** a task fails
- **AND** another task does not depend on any tag of the failed task
- **THEN** the other task SHALL be executed normally

### Requirement: A failed task is retried on the next run

A task marked failed SHALL be treated as not yet completed when the analysis is run again, and a task marked successful SHALL be skipped.

#### Scenario: Failed task runs again
- **WHEN** an analysis is run again after a task failed
- **THEN** that task SHALL be executed again

#### Scenario: Successful task is skipped
- **WHEN** an analysis is run again after a task succeeded
- **THEN** that task SHALL NOT be executed again

### Requirement: A schema violation is not a failure

A response that is parsable JSON but does not conform to the declared response schema SHALL be accepted and SHALL NOT mark the task failed, so that validation problems are reported without changing task outcomes.

#### Scenario: Non-conformant payload still succeeds
- **WHEN** a task's model response is parsable JSON that violates the declared response schema
- **THEN** the task SHALL be marked successful
- **AND** the violation SHALL be logged as a warning
