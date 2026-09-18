# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
