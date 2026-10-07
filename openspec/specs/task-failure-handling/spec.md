# task-failure-handling Specification

## Purpose

Defines when a task execution counts as failed, and what that failure implies for the tasks that depend on it, for the recorded execution state, and for the next run.

## Requirements

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

A task marked failed SHALL be treated as not yet completed when the analysis is run again, and a task marked successful SHALL be skipped. A task SHALL be marked failed only after the attempts it is allowed within the current run have been used, or after a failure that does not permit another attempt.

#### Scenario: Failed task runs again
- **WHEN** an analysis is run again after a task failed
- **THEN** that task SHALL be executed again

#### Scenario: Successful task is skipped
- **WHEN** an analysis is run again after a task succeeded
- **THEN** that task SHALL NOT be executed again

#### Scenario: A retryable failure is exhausted inside the run first
- **WHEN** a task step fails in a way that permits another attempt
- **AND** the attempts allowed for the run are not yet used
- **THEN** the task SHALL NOT yet be marked failed
- **AND** the step SHALL be attempted again in the same run

### Requirement: A task that cannot start counts as failed

A task whose execution cannot start SHALL be marked failed for that run. It SHALL NOT publish a response property, SHALL NOT unlock its dependents, SHALL be reported for that task, and SHALL be executed again on the next run.

#### Scenario: Loop target does not exist or is not an array
- **WHEN** a loop task's `loopOn` path does not exist or is not an array
- **THEN** the task SHALL be marked failed
- **AND** no response property SHALL be written for that task
- **AND** the failure SHALL be reported for that task
- **AND** the task SHALL NOT be executed again in the same run

#### Scenario: Dependents stay blocked
- **WHEN** a task is marked failed because its execution could not start
- **AND** another task declares a dependency on a tag of that task
- **THEN** the dependent task SHALL NOT be executed in that run

#### Scenario: The task runs again on the next run
- **WHEN** the analysis is run again after a task failed because its execution could not start
- **THEN** that task SHALL be executed again

#### Scenario: Other tasks are unaffected
- **WHEN** a task cannot start
- **AND** another task is pending with satisfied dependencies
- **THEN** the other task SHALL be executed normally

### Requirement: A non-conformant response fails the task once its attempts are used

A task step whose attempts were all rejected SHALL be marked failed, including when the last attempt produced a payload that parses but violates the declared response schema. The response property of that step SHALL NOT be written to the analysis results.

#### Scenario: Non-loop task with non-conformant attempts
- **WHEN** every attempt of a non-loop task produced a response that does not conform to the declared schema
- **THEN** the task SHALL be marked failed
- **AND** no response property SHALL be written for that task

#### Scenario: Loop task with a non-conformant index
- **WHEN** a loop task has at least one index whose attempts all produced a response that does not conform to the declared schema
- **THEN** the task SHALL be marked failed
- **AND** the indices that produced a conformant response SHALL stay recorded as successful
