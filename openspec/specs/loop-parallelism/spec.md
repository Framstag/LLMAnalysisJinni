# Loop Parallelism

## Purpose

Defines the engine's capability to execute loop task iterations concurrently using a configurable thread pool, with per-index completion tracking and thread-safe state persistence.

## Requirements

### Requirement: Parallel loop task execution

The engine SHALL support concurrent execution of loop task iterations using a configurable thread pool.

#### Scenario: Sequential execution with default parallelism
- **WHEN** `loopParallelism` is set to 1 (default) in the workspace config
- **THEN** loop iterations execute sequentially, one at a time

#### Scenario: Parallel execution with increased parallelism
- **WHEN** `loopParallelism` is set to N (N > 1) in the workspace config
- **THEN** up to N loop iterations execute concurrently using a thread pool

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