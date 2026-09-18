## REMOVED Requirements

### Requirement: Console trace toggleable via `--execution-trace`

**Reason**: The built-in default of `true` no longer holds. The TUI is the default display mode, so console logging outside the TUI is off unless it is turned on. The setting is also no longer governed by the CLI flag alone, because the workspace configuration can supply it and the CLI flag overrides it only when explicitly passed.

**Migration**: Use the new requirement "Console execution trace follows the configuration precedence order". `--execution-trace=true` still enables the verbose console output and still disables the TUI, so existing invocations that pass the flag keep their behaviour; invocations that relied on the trace being on by default now see the TUI instead.

## ADDED Requirements

### Requirement: Console execution trace follows the configuration precedence order

Console output of progressive chat messages SHALL be governed by the effective execution trace setting, which SHALL be resolved as: value explicitly passed as `--execution-trace`, then the workspace configuration, then the built-in default of disabled. An effective execution trace SHALL disable the TUI, and an inactive execution trace SHALL leave chat-message console output off and leave the TUI active when stdout is a terminal.

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
- **THEN** no chat messages SHALL appear on console (only task-level logs and errors)

#### Scenario: Console trace enabled through the workspace configuration
- **WHEN** the workspace configuration enables the execution trace
- **AND** `analyse` runs without `--execution-trace`
- **THEN** progressive chat messages SHALL appear on console
- **AND** the TUI SHALL NOT be started

#### Scenario: Console trace and TUI are mutually exclusive
- **WHEN** the effective execution trace is active
- **THEN** no TUI rendering SHALL occur
