# Spec Delta

## MODIFIED Requirements

### Requirement: Engine log output is diverted while the TUI owns the terminal

From the frame the TUI first paints until it closes, engine log records SHALL be written to a log file inside the workspace instead of the terminal. The file SHALL be overwritten at the start of each run, SHALL record the level, the logger, the task identifier when one is set, and the message, and SHALL NOT require any configuration or CLI option to be produced. The engine log files of a workspace SHALL be bounded in total size: a run SHALL keep the newest records within the bound and SHALL NOT grow the engine logs beyond it, whatever the code logs. Components that write diagnostics must go through the engine's logging path, so that no component can write to `stdout` or `stderr` while the TUI owns the terminal. Diagnostics a run emits before that first frame SHALL stay on the console: the display mode is not known before them, and a run with nothing to execute SHALL NOT start the TUI at all.

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

### Requirement: TUI shows the most recent warning or error

The TUI SHALL display the most recent `WARN` or `ERROR` record emitted during the run as a single truncated line within its frame, so that a condition not attached to one task row is visible before the run ends. The line SHALL be updated as newer records arrive and SHALL NOT change the region the TUI reserves for its other content. A record whose text equals the text the line currently shows SHALL NOT trigger a repaint of the frame.

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

#### Scenario: A repeated record does not repaint the frame
- **WHEN** a record is emitted whose text equals the text the reserved line currently shows
- **THEN** no frame SHALL be painted for that record
- **AND** the reserved line SHALL still show that record

#### Scenario: A newer record still repaints the frame
- **WHEN** a record is emitted whose text differs from the text the reserved line currently shows
- **THEN** the frame SHALL show the newer record
