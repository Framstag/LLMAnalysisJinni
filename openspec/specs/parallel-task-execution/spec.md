# parallel-task-execution Specification

## Purpose

Execute independent DAG tasks in parallel while respecting task dependencies, with thread-safe state management and attributable logging.

## Requirements

### Requirement: Parallel task execution with dependency ordering

The system SHALL execute multiple tasks concurrently when their dependencies are satisfied.

#### Scenario: Independent tasks run in parallel
- **WHEN** two or more tasks have all their `dependsOn` tags satisfied
- **THEN** the system SHALL execute them concurrently using a thread pool

#### Scenario: Blocked tasks wait for dependencies
- **WHEN** a task has unsatisfied `dependsOn` tags
- **THEN** the system SHALL NOT execute it until all its dependencies are met

#### Scenario: Newly unblocked tasks are dispatched promptly
- **WHEN** a task completes and its tags satisfy another task's dependencies
- **THEN** the system SHALL submit the newly unblocked task for execution

#### Scenario: All tasks complete
- **WHEN** all tasks have been executed (successfully or failed)
- **THEN** the scheduler SHALL terminate and return

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

### Requirement: Configurable parallelism

The system SHALL allow the user to control the degree of task-level parallelism.

#### Scenario: Default parallelism
- **WHEN** no `--task-parallelism` flag is passed
- **THEN** the system SHALL use the default parallelism of 2

#### Scenario: Custom parallelism via CLI
- **WHEN** `--task-parallelism=4` is passed
- **THEN** the system SHALL use 4 worker threads for DAG-level tasks

#### Scenario: Custom parallelism via config
- **WHEN** `config.json` contains `"taskParallelism": 8`
- **THEN** the system SHALL use 8 worker threads

### Requirement: `--single-step` works in parallel mode

The system SHALL support single-step execution in parallel mode.

#### Scenario: Single-step executes one task
- **WHEN** `--single-step` is passed
- **THEN** the system SHALL execute exactly one task (including all its loop indices) and then stop

### Requirement: MDC logging attributes log lines to tasks

The system SHALL tag every log line with the originating task context.

#### Scenario: Task ID in log output
- **WHEN** a worker thread logs a message during task execution
- **THEN** the log line SHALL include the task ID in the MDC context

#### Scenario: Loop index in log output
- **WHEN** a loop worker thread logs a message
- **THEN** the log line SHALL include both the task ID and the loop index

#### Scenario: MDC context is cleared after execution
- **WHEN** a task or loop worker completes
- **THEN** the MDC context SHALL be cleared to prevent leakage to subsequent tasks

### Requirement: Two-tier thread pools prevent deadlock

The system SHALL use separate thread pools for DAG-level tasks and loop-level workers to prevent thread starvation deadlock.

#### Scenario: DAG and loop pools are independent
- **WHEN** a loop task executes
- **THEN** its loop workers SHALL run in a separate pool from the DAG task scheduler
- **AND** loop workers SHALL NOT compete with DAG tasks for threads

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
