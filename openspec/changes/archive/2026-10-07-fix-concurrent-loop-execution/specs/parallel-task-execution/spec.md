# Spec Delta

## MODIFIED Requirements

### Requirement: Thread-safe state mutations

The system SHALL protect shared state from concurrent access by parallel workers.

#### Scenario: Concurrent state updates are serialized
- **WHEN** two workers call `StateManager.updateState()` concurrently
- **THEN** the calls SHALL be serialized (one waits for the other to complete)

#### Scenario: Concurrent task completion is serialized
- **WHEN** two workers call `TaskManager.markTaskAsSuccessful()` concurrently
- **THEN** the calls SHALL be serialized and both tasks SHALL be marked correctly

#### Scenario: Concurrent loop tasks write into the same analysis array
- **WHEN** two loop workers of different loop tasks write different properties of the same analysis array entry
- **THEN** the writes SHALL be serialized
- **AND** neither write SHALL be lost
- **AND** each write SHALL be persisted by the state save that follows it

## ADDED Requirements

### Requirement: Scheduler progress requires a state change

The scheduler SHALL dispatch a task only while the task is pending and its status has not been left unchanged by an execution of the same run. An execution that ends without changing its task's status SHALL be reported for that task and SHALL NOT cause another dispatch of that task in the same run.

#### Scenario: An execution without a status change is not dispatched again
- **WHEN** a task execution ends without marking its task successful or failed
- **THEN** the scheduler SHALL NOT submit that task again in the same run
- **AND** the number of dispatches of that task in the run SHALL be one

#### Scenario: The condition is reported once
- **WHEN** the scheduler does not dispatch a task because its execution left no status change
- **THEN** the run SHALL report that condition for that task exactly once
- **AND** the report SHALL name the task

#### Scenario: A completion still dispatches the tasks it unlocks
- **WHEN** a task completes and its tags satisfy the dependencies of another task
- **THEN** the scheduler SHALL submit the unlocked task

#### Scenario: The run ends with tasks that cannot make progress
- **WHEN** every remaining pending task has unsatisfied dependencies
- **THEN** the run SHALL end
- **AND** the run SHALL name the tasks that could not be dispatched
- **AND** the run SHALL NOT keep dispatching a pending task to make progress
