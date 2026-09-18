# live-progress-display Specification

## Purpose

Provide a live-updating terminal UI showing task execution progress during analysis, with per-worker interaction timelines, loop progress, timing, and token usage. Falls back to simple sequential status lines when no TTY is available.

## Requirements

### Requirement: Live TUI shows task execution progress

The system SHALL display a live-updating terminal UI showing all tasks in the DAG with their current execution status. A task that was already successful in an earlier run SHALL be displayed with its successful status from the first frame the TUI paints; no frame SHALL show such a task as pending.

#### Scenario: TUI shows all tasks on start
- **WHEN** analysis begins and at least one task has never been executed successfully
- **THEN** the TUI SHALL display all tasks from the DAG with their initial status (pending)

#### Scenario: Already successful tasks are correct in the first frame
- **WHEN** analysis begins and some tasks were already successful in an earlier run
- **THEN** the first frame the TUI paints SHALL show those tasks as successful
- **AND** no frame painted by the run SHALL show them as pending

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

When stdout is not a terminal (piped output, CI, IDE run configuration, or a launcher that does not attach a console), the system SHALL fall back to simple sequential status lines without cursor manipulation or colour. Detection SHALL be based on whether stdout is a terminal, and SHALL NOT depend on the availability of a JVM console object.

#### Scenario: Non-TTY detects piped output
- **WHEN** stdout is not a terminal
- **THEN** the system SHALL NOT attempt TUI rendering

#### Scenario: TUI starts despite an unattached JVM console
- **WHEN** stdout is a terminal
- **AND** no JVM console object is available to the process
- **THEN** the system SHALL start the TUI

#### Scenario: Fallback mode and its reason are reported
- **WHEN** the system does not start the TUI
- **THEN** the run SHALL report which display mode is used instead
- **AND** it SHALL report the reason the TUI was not started

#### Scenario: Non-TTY prints one line per event
- **WHEN** a task starts
- **THEN** the system SHALL print a line with status icon, task name, and loop progress (if applicable)
- **WHEN** a task completes
- **THEN** the system SHALL print a line with status icon, task name, and elapsed time

### Requirement: `--execution-trace` flag disables TUI

Console execution trace SHALL be the verbose SLF4J output of chat activity. Its effective value SHALL follow the configuration precedence order, and it SHALL default to disabled. Whenever the execution trace is effective, the system SHALL NOT start the TUI and SHALL emit the verbose console output instead. Whenever the execution trace is not effective and stdout is a terminal, the TUI SHALL be the display mode, and from the frame the TUI first paints until it closes, SLF4J console output SHALL NOT be written to the terminal.

#### Scenario: execution-trace disables TUI
- **WHEN** `--execution-trace=true` is passed
- **THEN** the TUI SHALL NOT be started
- **AND** SLF4J console output SHALL be active (verbose behaviour)

#### Scenario: execution-trace defaults to false
- **WHEN** no `--execution-trace` flag is passed
- **AND** the workspace configuration does not enable the execution trace
- **THEN** the TUI SHALL be the default display mode
- **AND** SLF4J console output SHALL NOT be written to the terminal from the frame the TUI first paints (log files still written)

#### Scenario: Config enables the execution trace
- **WHEN** the workspace configuration enables the execution trace
- **AND** no `--execution-trace` flag is passed
- **THEN** the TUI SHALL NOT be started
- **AND** SLF4J console output SHALL be active

#### Scenario: Explicit flag overrides config
- **WHEN** the workspace configuration enables the execution trace
- **AND** `--execution-trace=false` is passed
- **THEN** the TUI SHALL be the display mode when stdout is a terminal

#### Scenario: TUI and console logging are mutually exclusive
- **WHEN** any execution trace is active
- **THEN** no TUI rendering SHALL occur
- **AND** no TUI rendering SHALL be interleaved with SLF4J console output

#### Scenario: No log record reaches the terminal while the TUI owns the terminal
- **WHEN** the TUI is the display mode
- **AND** the TUI has painted a frame
- **AND** any component emits a log record at any level during the run
- **THEN** the record SHALL NOT be written to the terminal
- **AND** no log record text SHALL appear inside or between painted frames

### Requirement: Log files always written regardless of display mode

The system SHALL always write full conversation logs to `logs/*.log` regardless of whether TUI or `--execution-trace` mode is active.

#### Scenario: Log files written in TUI mode
- **WHEN** analysis runs in TUI mode
- **THEN** full conversation logs SHALL be written to `logs/<taskId>[_<loopIndex>].log`

#### Scenario: Log files written in execution-trace mode
- **WHEN** analysis runs with `--execution-trace`
- **THEN** full conversation logs SHALL still be written to `logs/<taskId>[_<loopIndex>].log`

### Requirement: Engine log output is diverted while the TUI owns the terminal

From the frame the TUI first paints until it closes, engine log records SHALL be written to a log file inside the workspace instead of the terminal. The file SHALL be overwritten at the start of each run, SHALL record the level, the logger, the task identifier when one is set, and the message, and SHALL NOT require any configuration or CLI option to be produced. Components that write diagnostics must go through the engine's logging path, so that no component can write to `stdout` or `stderr` while the TUI owns the terminal. Diagnostics a run emits before that first frame SHALL stay on the console: the display mode is not known before them, and a run with nothing to execute SHALL NOT start the TUI at all.

#### Scenario: Log records are written to the workspace log file
- **WHEN** analysis runs in TUI mode
- **AND** the TUI has painted a frame
- **AND** a component emits a log record
- **THEN** the record SHALL appear in `<workspace>/logs/engine.log`
- **AND** the record SHALL NOT appear on the terminal

#### Scenario: Diagnostics before the first frame stay on the console
- **WHEN** a run in TUI mode emits a diagnostic before the display decision is made
- **THEN** the diagnostic SHALL be written to the console
- **AND** the run SHALL still write the records emitted after the first frame to the engine log file

#### Scenario: Log file is overwritten per run
- **WHEN** analysis runs twice in the same workspace in TUI mode
- **THEN** the log file of the second run SHALL contain the records of the second run only

#### Scenario: Records carry the task identifier
- **WHEN** a log record is emitted by a task execution
- **THEN** the record in the log file SHALL identify that task

#### Scenario: Tool diagnostics cannot bypass the routing
- **WHEN** a tool hits an error that it reports as a diagnostic
- **AND** the TUI is the display mode
- **THEN** the diagnostic SHALL follow the same routing as any other log record
- **AND** no component SHALL write it directly to the terminal

#### Scenario: Non-TUI runs keep their console diagnostics
- **WHEN** stdout is not a terminal, or the execution trace is active
- **THEN** the run SHALL NOT divert its log output to the engine log file
- **AND** the console SHALL keep the diagnostics it produced before this change

### Requirement: TUI shows the most recent warning or error

The TUI SHALL display the most recent `WARN` or `ERROR` record emitted during the run as a single truncated line within its frame, so that a condition not attached to one task row is visible before the run ends. The line SHALL be updated as newer records arrive and SHALL NOT change the region the TUI reserves for its other content.

#### Scenario: Warning appears in the frame
- **WHEN** the TUI is the display mode
- **AND** a `WARN` record is emitted
- **THEN** the frame SHALL contain a line showing that record
- **AND** the line SHALL be truncated to the available frame width

#### Scenario: Latest record replaces the previous one
- **WHEN** a second `WARN` or `ERROR` record is emitted
- **THEN** the line SHALL show the newer record
- **AND** the number of lines the frame occupies SHALL NOT change

#### Scenario: No warning leaves the line empty
- **WHEN** no `WARN` or `ERROR` record has been emitted in the run
- **THEN** the frame SHALL reserve the line without showing a stale message

#### Scenario: Frame accounting stays correct
- **WHEN** the TUI repaints a frame after a warning was shown
- **THEN** the repaint SHALL overwrite exactly the region of the frame it previously rendered
- **AND** it SHALL NOT overwrite content written before the TUI started

### Requirement: Terminal control sequences are only emitted for capable terminals

The system SHALL emit cursor movement and screen erase sequences only when the output terminal supports ANSI control sequences. On a terminal without that support, the system SHALL render without cursor manipulation, and it SHALL not move the cursor above lines it did not itself write.

#### Scenario: No escape sequences on a dumb terminal
- **WHEN** the TUI runs on a terminal that does not support ANSI control sequences
- **THEN** no cursor movement or screen erase sequence SHALL be written to stdout

#### Scenario: First paint does not disturb existing output
- **WHEN** the TUI renders its first frame
- **THEN** the system SHALL NOT move the cursor above content written before the TUI started

#### Scenario: Repaint preserves previous frame position
- **WHEN** the TUI repaints an updated frame on a capable terminal
- **THEN** the system SHALL overwrite exactly the region of the frame it previously rendered

### Requirement: A run with nothing to execute is reported

When no task of the analysis can be executed because every task is already successful, the system SHALL state that explicitly, SHALL NOT start the live terminal UI, and SHALL NOT print a completion summary that reports task outcomes as if work had been performed. The run SHALL exit with status 0.

#### Scenario: Nothing to execute is stated
- **WHEN** `analyse` runs against a workspace in which every task is already successful
- **THEN** the run SHALL state that no task is runnable
- **AND** the statement SHALL make clear that all tasks are already successful

#### Scenario: No live TUI for a run without runnable tasks
- **WHEN** `analyse` runs in an environment whose stdout is a terminal
- **AND** every task is already successful
- **THEN** the system SHALL NOT start the TUI
- **AND** it SHALL NOT paint a frame listing the tasks as pending

#### Scenario: Exit status of a run without runnable tasks
- **WHEN** `analyse` runs and no task is runnable
- **THEN** the run SHALL exit with status 0

#### Scenario: A workspace with runnable tasks is unaffected
- **WHEN** `analyse` runs and at least one task is runnable
- **THEN** the system SHALL use the display mode it would otherwise use
- **AND** it SHALL NOT state that no task is runnable

### Requirement: TUI runs without restricted native access warnings

The environment the TUI runs in SHALL grant the terminal implementation the native access it requires, so that starting the TUI emits no restricted-native-access warning and the TUI does not become unavailable on a JVM release that blocks restricted methods. The packaged artefact SHALL declare that access in its own metadata.

#### Scenario: No restricted native access warning from the artefact
- **WHEN** the packaged artefact is started
- **THEN** its output SHALL NOT contain a restricted-native-access warning

#### Scenario: Native access declared by the artefact
- **WHEN** the packaged artefact is inspected
- **THEN** it SHALL declare native access for the unnamed module in its metadata
