## ADDED Requirements

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

After all tool-call rounds of a single `executeMessages()` call complete, a log file SHALL be written containing the full conversation with metadata.

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
- **THEN** the log file SHALL be written to `<workspace>/logs/<taskId>_<index>.log` (or similar deterministic path)
- **AND** existing log files SHALL be overwritten (no versioning, no append)

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

Log files SHALL be written for every task execution. No CLI flag controls this.

#### Scenario: File written for every execution
- **WHEN** any task executes
- **THEN** a log file SHALL be written to `<workspace>/logs/<taskId>[_<loopIndex>].log`
- **AND** no CLI option is required to enable this

### Requirement: Console trace toggleable via `--execution-trace`

Console output of progressive chat messages SHALL be controlled by a CLI flag `--execution-trace`. Default: `true`.

#### Scenario: Console trace on by default
- **WHEN** `analyse` runs without `--execution-trace`
- **THEN** progressive chat messages SHALL appear on console

#### Scenario: Console trace disabled
- **WHEN** `analyse` runs with `--execution-trace=false`
- **THEN** no chat messages SHALL appear on console (only task-level logs and errors)

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
