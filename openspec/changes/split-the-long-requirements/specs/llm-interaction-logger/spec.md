# Spec Delta

## ADDED Requirements

### Requirement: The console output of the chat messages follows the execution trace

The console output of the progressive chat messages SHALL be governed by the effective execution trace setting.

The console execution trace SHALL be the verbose SLF4J output of the chat activity.

An effective execution trace SHALL leave the progressive chat messages on the console, and an inactive execution trace SHALL leave them off the console.

#### Scenario: The chat messages stay off the console by default

- **WHEN** a run starts without `--execution-trace`
- **AND** the workspace configuration does not enable the execution trace
- **THEN** no progressive chat message SHALL be written to the console

#### Scenario: An explicit flag enables the chat messages

- **WHEN** `--execution-trace=true` is passed
- **THEN** the progressive chat messages SHALL be written to the console

#### Scenario: An explicit flag disables the chat messages

- **WHEN** `--execution-trace=false` is passed
- **THEN** no progressive chat message SHALL be written to the console

#### Scenario: The workspace configuration can enable the chat messages

- **WHEN** the workspace configuration enables the execution trace
- **AND** no `--execution-trace` flag is passed
- **THEN** the progressive chat messages SHALL be written to the console

## REMOVED Requirements

### Requirement: Console execution trace follows the configuration precedence order

**Reason**: The requirement stated the console output and the display mode of the run in one place, and the display mode belongs to the capability that owns it.
**Migration**: The console output under the trace is stated by the requirement `The console output of the chat messages follows the execution trace`, the display mode is stated by the requirement `--execution-trace flag disables TUI` of the capability `live-progress-display`, and the order the effective value is resolved in is stated by the capability `cli-config-precedence`.
