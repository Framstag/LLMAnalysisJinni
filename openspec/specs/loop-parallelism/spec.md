# Loop Parallelism

## Purpose

Defines the engine's capability to execute loop task iterations concurrently using a configurable thread pool, with per-index completion tracking and thread-safe state persistence.

## Requirements

### Requirement: Parallel loop task execution

The engine SHALL support concurrent execution of loop task iterations using a configurable thread pool. The loop cursor of a loop task SHALL belong to that task execution and SHALL NOT be shared with another execution, so that a loop task SHALL NOT be prevented from executing because another loop task is executing.

#### Scenario: Sequential execution with default parallelism
- **WHEN** `loopParallelism` is set to 1 (default) in the workspace config
- **THEN** loop iterations execute sequentially, one at a time

#### Scenario: Parallel execution with increased parallelism
- **WHEN** `loopParallelism` is set to N (N > 1) in the workspace config
- **THEN** up to N loop iterations execute concurrently using a thread pool

#### Scenario: Two loop tasks execute concurrently
- **WHEN** two loop tasks whose dependencies are satisfied both loop on the same analysis array
- **THEN** both loop tasks SHALL execute their iterations
- **AND** neither loop task SHALL be rejected or aborted because the other one is executing

#### Scenario: Loop cursor is not shared between executions
- **WHEN** one loop task is executing its iterations
- **AND** another loop task starts
- **THEN** the second loop task SHALL obtain its own cursor over its loop target
- **AND** the first loop task SHALL NOT observe the second loop task's cursor or loop index

### Requirement: Per-index completion tracking

The system SHALL track successful loop indices in a set rather than a single last-successful index.

#### Scenario: Concurrent completions
- **WHEN** multiple loop iterations complete concurrently out of order (e.g., indices 3, 1, 5)
- **THEN** all successfully completed indices SHALL be recorded

#### Scenario: Restart skips only completed indices
- **WHEN** an analysis restarts after partial loop completion (indices {0, 2, 4} done)
- **THEN** only uncompleted indices {1, 3, 5...} SHALL execute on restart

#### Scenario: Task completion clears per-index tracking
- **WHEN** all loop iterations complete successfully for a task
- **THEN** the successful indices set SHALL be cleared and the task marked SUCCESSFUL

### Requirement: Thread-safe state persistence

State updates from worker threads SHALL be protected from concurrent access.

#### Scenario: Concurrent state writes
- **WHEN** multiple worker threads complete simultaneously
- **THEN** each thread's result SHALL be persisted without data loss or corruption

#### Scenario: State file integrity
- **WHEN** a worker writes to the state file
- **THEN** the file SHALL contain a complete, valid serialization of all persisted data

### Requirement: Configurable parallelism

The system SHALL expose `loopParallelism` as a configuration property in the workspace config.

#### Scenario: Config file set
- **WHEN** the workspace config contains `loopParallelism: 4`
- **THEN** the engine SHALL use a thread pool of 4 threads for loop tasks

#### Scenario: Config file unset
- **WHEN** the workspace config does not contain `loopParallelism`
- **THEN** the engine SHALL default to 1 (sequential execution)

### Requirement: The loop cursor belongs to one task execution

A loop task execution SHALL obtain its loop cursor when the execution starts and SHALL release it when the execution ends, so that a cursor never outlives the execution that owns it. Two executions SHALL be able to hold a cursor over the same analysis array at the same time, and a `loopOn` target that does not exist or is not an array SHALL be reported for the task that requested it.

#### Scenario: Cursor is released when the execution ends
- **WHEN** a loop task execution ends, whether every index succeeded, an index failed, or the execution could not start
- **THEN** the cursor of that execution SHALL be released
- **AND** a later loop task SHALL be able to obtain its own cursor

#### Scenario: Broken loop target is reported for its task
- **WHEN** a loop task's `loopOn` path does not exist or is not an array
- **THEN** that task SHALL fail with a message naming the path
- **AND** the failure SHALL NOT prevent another loop task from executing

#### Scenario: Concurrent writes into the same analysis array
- **WHEN** two loop tasks write different response properties of the same entry of the analysis array
- **THEN** both properties SHALL be stored in the analysis state
- **AND** the state file of the workspace SHALL remain a complete, valid serialization

#### Scenario: Loop indices of concurrent tasks are recorded per task
- **WHEN** two loop tasks execute concurrently and each has an index that succeeds
- **THEN** each successful index SHALL be recorded for its own task
- **AND** a later run SHALL skip exactly the indices recorded as successful
