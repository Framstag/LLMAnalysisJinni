## MODIFIED Requirements

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

Console execution trace SHALL be the verbose SLF4J output of chat activity. Its effective value SHALL follow the configuration precedence order, and it SHALL default to disabled. Whenever the execution trace is effective, the system SHALL NOT start the TUI and SHALL emit the verbose console output instead. Whenever the execution trace is not effective and stdout is a terminal, the TUI SHALL be the display mode and SLF4J console output SHALL be suppressed.

#### Scenario: execution-trace disables TUI
- **WHEN** `--execution-trace=true` is passed
- **THEN** the TUI SHALL NOT be started
- **AND** SLF4J console output SHALL be active (verbose behaviour)

#### Scenario: execution-trace defaults to false
- **WHEN** no `--execution-trace` flag is passed
- **AND** the workspace configuration does not enable the execution trace
- **THEN** the TUI SHALL be the default display mode
- **AND** SLF4J console output SHALL be suppressed (log files still written)

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

## ADDED Requirements

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
