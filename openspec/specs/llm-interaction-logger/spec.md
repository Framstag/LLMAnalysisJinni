# llm-interaction-logger Specification

## Purpose

Record and expose LLM chat interactions for debugging, audit, and traceability. Console provides progressive real-time per-round message summary. File provides authoritative complete multi-round conversation with thinking traces and token usage.

## Requirements

### Requirement: Console logging shows each message exactly once per multi-round execution

During a single `ChatExecutor.executeMessages()` call spanning multiple tool-call round-trips, each chat message SHALL appear on console exactly once, in chronological order, with no duplication across rounds.

System messages SHALL be excluded from console output by default. A CLI flag `--execution-trace-system` SHALL enable system message display on console.

#### Scenario: Messages from all rounds appear once in order
- **WHEN** `executeMessages()` makes 3 round-trips (initial + 2 tool rounds) with messages [sys, user, ai1, tr1, ai2, tr2, ai3]
- **THEN** console output SHALL contain all 7 messages in order, each prefixed with its type indicator
- **AND** no message SHALL appear more than once

#### Scenario: Tool result messages are visible
- **WHEN** the LLM requests tool execution and tool results are returned to the conversation
- **THEN** each `ToolExecutionResultMessage` SHALL appear on console as `<-- Tool <name>: <result>`

#### Scenario: System prompt is shown only with `--execution-trace-system`
- **WHEN** the first request carries a `SystemMessage`
- **AND** `--execution-trace-system` is NOT set
- **THEN** the system message SHALL NOT appear on console
- **WHEN** `--execution-trace-system` IS set
- **THEN** the system message SHALL appear on console as `##> <text>`

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

### Requirement: Console output is separate from file log

Console output SHALL be a progressive real-time summary. File output SHALL be the authoritative complete record.

#### Scenario: Console and file serve different roles
- **WHEN** an execution runs
- **THEN** console SHALL show progressive per-round messages (terse, one line per message)
- **AND** the file SHALL contain the full message bodies, thinking traces, and token metadata
- **AND** neither SHALL depend on the other

### Requirement: Console shows thinking traces

When the model returns an `AiMessage` with a non-null `thinking()` field, the console SHALL display the thinking content.

#### Scenario: Thinking shown on console
- **WHEN** the model returns an `AiMessage` with a non-null `thinking()` field
- **THEN** console SHALL show `> Thinking: <thinking trace>` on a separate line after the AI response text

### Requirement: File logging is always-on

Log files SHALL be written for every attempt of a task step. No CLI flag controls this.

#### Scenario: File written for every execution
- **WHEN** any task step is attempted
- **THEN** a log file SHALL be written for that attempt to `<workspace>/logs/<taskId>[_<loopIndex>][.attempt<N>].log`
- **AND** no CLI option is required to enable this

### Requirement: Thread safety by instance isolation

Each `ChatExecutor` instance SHALL maintain its own logging state. No shared mutable state between concurrent executions.

#### Scenario: Parallel loop workers don't interfere
- **WHEN** two threads call `executeMessages()` on separate `ChatExecutor` instances concurrently
- **THEN** each thread's console output SHALL contain only its own messages
- **AND** each thread's file log SHALL contain only its own conversation
- **AND** no `synchronized` blocks or `ThreadLocal` SHALL be required for logging state

### Requirement: Streaming compatibility

The logging design SHALL support future streaming without structural changes.

#### Scenario: Request messages logged before streaming starts
- **WHEN** `executeMessages()` calls the LLM (synchronous or streaming)
- **THEN** request messages SHALL be logged to console BEFORE the LLM call begins
- **AND** the same console logging code SHALL work for both synchronous and streaming modes

#### Scenario: File logging after completion
- **WHEN** a streaming execution completes (all tokens received, final response assembled)
- **THEN** the file log SHALL be written using the same code path as synchronous mode

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
