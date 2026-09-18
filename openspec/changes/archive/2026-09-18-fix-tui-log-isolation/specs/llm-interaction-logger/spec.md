# Spec Delta

## MODIFIED Requirements

### Requirement: Console execution trace follows the configuration precedence order

Console output of progressive chat messages SHALL be governed by the effective execution trace setting, which SHALL be resolved as: value explicitly passed as `--execution-trace`, then the workspace configuration, then the built-in default of disabled. An effective execution trace SHALL disable the TUI, and an inactive execution trace SHALL leave chat-message console output off and leave the TUI active when stdout is a terminal. Console output of task-level logs and errors is only produced when the TUI does not own the terminal; the TUI owns the terminal from its first painted frame until it closes, and from that frame on those records are diverted away from it.

#### Scenario: Console trace off by default
- **WHEN** `analyse` runs without `--execution-trace`
- **AND** the workspace configuration does not enable the execution trace
- **THEN** no progressive chat messages SHALL appear on console
- **AND** the TUI SHALL be the display mode when stdout is a terminal

#### Scenario: Console trace enabled explicitly
- **WHEN** `analyse` runs with `--execution-trace=true`
- **THEN** progressive chat messages SHALL appear on console
- **AND** the TUI SHALL NOT be started

#### Scenario: Console trace disabled explicitly
- **WHEN** `analyse` runs with `--execution-trace=false`
- **AND** the TUI is not the display mode
- **THEN** no chat messages SHALL appear on console (only task-level logs and errors)

#### Scenario: Console trace disabled and the TUI owns the terminal
- **WHEN** `analyse` runs with `--execution-trace=false`
- **AND** stdout is a terminal
- **THEN** the TUI SHALL be the display mode
- **AND** no chat messages, task-level logs or errors SHALL be written to the terminal

#### Scenario: Console trace enabled through the workspace configuration
- **WHEN** the workspace configuration enables the execution trace
- **AND** `analyse` runs without `--execution-trace`
- **THEN** progressive chat messages SHALL appear on console
- **AND** the TUI SHALL NOT be started

#### Scenario: Console trace and TUI are mutually exclusive
- **WHEN** the effective execution trace is active
- **THEN** no TUI rendering SHALL occur
