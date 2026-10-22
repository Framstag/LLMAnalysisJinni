# Spec Delta

## MODIFIED Requirements

### Requirement: `--execution-trace` flag disables TUI

The effective value of the execution trace SHALL follow the configuration precedence order, and SHALL default to disabled.

Whenever the execution trace is effective, the system SHALL NOT start the TUI and SHALL emit the verbose console output instead.

Whenever the execution trace is not effective and stdout is a terminal, the TUI SHALL be the display mode, and from the frame the TUI first paints until it closes, SLF4J console output SHALL NOT be written to the terminal.

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

### Requirement: Engine log output is diverted while the TUI owns the terminal

From the frame the TUI first paints until it closes, engine log records SHALL be written to a log file inside the workspace instead of the terminal.

The file SHALL be overwritten per run and SHALL NOT require any configuration or CLI option to be produced.

The engine log files of a workspace SHALL stay within a fixed total size bound, and a run SHALL keep the newest records within that bound.

Diagnostics emitted before that first frame SHALL stay on the console.

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

#### Scenario: Engine logs stay within the bound

- **WHEN** a run emits many more records than the bound holds
- **THEN** the engine log files of the workspace SHALL together stay within the bound
- **AND** the newest records of the run SHALL be present

#### Scenario: A repeated record cannot grow the engine log without limit

- **WHEN** a run emits the same record millions of times
- **THEN** the engine log files of the workspace SHALL stay within the bound
- **AND** the run SHALL NOT fail because of the log volume

#### Scenario: The engine log file records the level, the logger and the message

- **WHEN** a log record is written to the engine log file
- **THEN** the record SHALL state its level, its logger and its message
