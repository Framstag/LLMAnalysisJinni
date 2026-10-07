# Spec Delta

## ADDED Requirements

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
