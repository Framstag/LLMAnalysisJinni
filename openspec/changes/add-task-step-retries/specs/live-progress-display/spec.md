# Spec Delta

## MODIFIED Requirements

### Requirement: Log files always written regardless of display mode

The system SHALL always write full conversation logs to `logs/*.log` regardless of whether TUI or `--execution-trace` mode is active. Every attempt of a task step SHALL leave its own log file, so retries are visible in the log directory in every display mode.

#### Scenario: Log files written in TUI mode
- **WHEN** analysis runs in TUI mode
- **THEN** full conversation logs SHALL be written to `logs/<taskId>[_<loopIndex>][.attempt<N>].log`
- **AND** a retried step SHALL leave one file per attempt

#### Scenario: Log files written in execution-trace mode
- **WHEN** analysis runs with `--execution-trace`
- **THEN** full conversation logs SHALL still be written to `logs/<taskId>[_<loopIndex>][.attempt<N>].log`
- **AND** a retried step SHALL leave one file per attempt
