# Spec Delta

## MODIFIED Requirements

### Requirement: File log captures complete multi-round conversation

After all tool-call rounds of a single `executeMessages()` call complete, a log file SHALL be written containing the full conversation with metadata. Each attempt of a task step SHALL write its own log file, so that the transcript of an attempt that was rejected is not overwritten by a later attempt of the same step.

#### Scenario: All messages written to file
- **WHEN** `executeMessages()` finishes (all tool rounds done, JSON result returned)
- **THEN** a log file SHALL exist containing every `ChatMessage` sent and received during the execution
- **AND** message type SHALL be labeled (System, User, AI, ToolExecutionResult)
- **AND** the file SHALL include aggregate token usage (input, output, total)

#### Scenario: Thinking traces captured when available
- **WHEN** the model returns an `AiMessage` with a non-null `thinking()` field
- **THEN** the thinking content SHALL appear in the file log under the AI message
- **AND** thinking SHALL be visually separated from the response text

#### Scenario: File path is deterministic
- **WHEN** an execution completes
- **THEN** the first attempt of a task step SHALL be written to `<workspace>/logs/<taskId>[_<loopIndex>].log`
- **AND** a further attempt of the same step SHALL be written to `<workspace>/logs/<taskId>[_<loopIndex>].attempt<N>.log`, where `<N>` is its attempt number
- **AND** the log file of an attempt that was rejected SHALL NOT be overwritten by a later attempt of the same step
- **AND** a later run that repeats an attempt of the same step SHALL overwrite that attempt's log file

### Requirement: File logging is always-on

Log files SHALL be written for every attempt of a task step. No CLI flag controls this.

#### Scenario: File written for every execution
- **WHEN** any task step is attempted
- **THEN** a log file SHALL be written for that attempt to `<workspace>/logs/<taskId>[_<loopIndex>][.attempt<N>].log`
- **AND** no CLI option is required to enable this
