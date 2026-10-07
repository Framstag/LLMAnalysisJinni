# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
