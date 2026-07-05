## ADDED Requirements

### Requirement: Live TUI shows task execution progress

The system SHALL display a live-updating terminal UI showing all tasks in the DAG with their current execution status.

#### Scenario: TUI shows all tasks on start
- **WHEN** analysis begins
- **THEN** the TUI SHALL display all tasks from the DAG with their initial status (pending)

#### Scenario: Task status updates live
- **WHEN** a task transitions from pending to running
- **THEN** the TUI SHALL update that task's row to show the running state within 500ms

#### Scenario: Task completion updates live
- **WHEN** a task completes successfully
- **THEN** the TUI SHALL mark that task as successful and show its elapsed time

#### Scenario: Task failure shows inline error
- **WHEN** a task fails
- **THEN** the TUI SHALL mark that task as failed and display the error message inline on the task row

### Requirement: TUI shows running time per task

The system SHALL display the elapsed wall-clock time for each task.

#### Scenario: Running task shows live elapsed time
- **WHEN** a task is running
- **THEN** the TUI SHALL show the elapsed time updating at least every second

#### Scenario: Completed task shows final elapsed time
- **WHEN** a task completes
- **THEN** the TUI SHALL show the final elapsed time on the task row

### Requirement: TUI shows per-worker interaction timeline for loop tasks

For loop tasks with parallel workers, the system SHALL display a sub-row per worker showing a live interaction timeline.

#### Scenario: Loop task shows worker sub-rows
- **WHEN** a loop task starts with N indices and parallelism P
- **THEN** the TUI SHALL display up to P worker sub-rows under the parent task

#### Scenario: Worker shows interaction steps
- **WHEN** a worker sends a request to the LLM
- **THEN** the TUI SHALL append a `→` symbol to that worker's interaction timeline

#### Scenario: Worker shows response received
- **WHEN** a worker receives a response from the LLM
- **THEN** the TUI SHALL append a `←` symbol to that worker's interaction timeline

#### Scenario: Worker shows tool call
- **WHEN** a worker executes a tool call
- **THEN** the TUI SHALL append a `◆` symbol to that worker's interaction timeline

#### Scenario: Worker shows tool result
- **WHEN** a worker receives a tool execution result
- **THEN** the TUI SHALL append a `✓` symbol to that worker's interaction timeline

#### Scenario: Worker shows round count
- **WHEN** a worker completes a full request-response round
- **THEN** the TUI SHALL update the round counter for that worker

#### Scenario: Worker shows completion
- **WHEN** a worker completes all interactions
- **THEN** the TUI SHALL mark that worker row as successful

### Requirement: TUI shows loop progress on parent task

The system SHALL display overall loop progress (completed indices / total indices) on the parent loop task row.

#### Scenario: Loop progress updates
- **WHEN** a loop worker completes
- **THEN** the parent task row SHALL update its loop progress counter

### Requirement: TUI shows per-task token usage

The system SHALL display input and output token counts per task.

#### Scenario: Token usage shown on task row
- **WHEN** a task completes
- **THEN** the TUI SHALL show the input and output token count on that task's row

### Requirement: TUI shows aggregate token usage and elapsed time

The system SHALL display aggregate token usage across all tasks and total elapsed time in a footer area.

#### Scenario: Footer shows aggregate tokens
- **WHEN** any task reports token usage
- **THEN** the TUI footer SHALL update the aggregate token counts

#### Scenario: Footer shows elapsed time
- **WHEN** analysis is running
- **THEN** the TUI footer SHALL show total elapsed time updating at least every second

### Requirement: TUI uses intuitive colour scheme

The system SHALL use colour to convey task status at a glance.

#### Scenario: Colour indicates status
- **WHEN** a task is pending
- **THEN** it SHALL be displayed in dim/gray
- **WHEN** a task is running
- **THEN** it SHALL be displayed in bright cyan
- **WHEN** a task is successful
- **THEN** it SHALL be displayed in green
- **WHEN** a task has failed
- **THEN** it SHALL be displayed in red

### Requirement: Non-TTY fallback outputs sequential status lines

When no terminal is available (piped output, CI), the system SHALL fall back to simple sequential status lines without cursor manipulation or colour.

#### Scenario: Non-TTY detects piped output
- **WHEN** stdout is not a terminal
- **THEN** the system SHALL NOT attempt TUI rendering

#### Scenario: Non-TTY prints one line per event
- **WHEN** a task starts
- **THEN** the system SHALL print a line with status icon, task name, and loop progress (if applicable)
- **WHEN** a task completes
- **THEN** the system SHALL print a line with status icon, task name, and elapsed time

### Requirement: `--execution-trace` flag disables TUI

When the `--execution-trace` flag is set, the system SHALL disable the TUI and fall back to the current SLF4J console output behaviour.

#### Scenario: execution-trace disables TUI
- **WHEN** `--execution-trace=true` is passed
- **THEN** the TUI SHALL NOT be started
- **AND** SLF4J console output SHALL be active (current verbose behaviour)

#### Scenario: execution-trace defaults to false
- **WHEN** no `--execution-trace` flag is passed
- **THEN** the TUI SHALL be the default display mode
- **AND** SLF4J console output SHALL be suppressed (log files still written)

### Requirement: Log files always written regardless of display mode

The system SHALL always write full conversation logs to `logs/*.log` regardless of whether TUI or `--execution-trace` mode is active.

#### Scenario: Log files written in TUI mode
- **WHEN** analysis runs in TUI mode
- **THEN** full conversation logs SHALL be written to `logs/<taskId>[_<loopIndex>].log`

#### Scenario: Log files written in execution-trace mode
- **WHEN** analysis runs with `--execution-trace`
- **THEN** full conversation logs SHALL still be written to `logs/<taskId>[_<loopIndex>].log`
