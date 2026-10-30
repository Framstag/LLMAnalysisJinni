# streaming-chat-model Specification

## Purpose

Provides an optional streaming transport for model invocations against OpenAI-compatible endpoints, so that long-running completions keep the HTTP connection actively fed with incremental data instead of one blocking transfer, while the task-step response contract stays identical to the established non-streaming mode.

## Requirements

### Requirement: Non-streaming operation is the default

The system SHALL execute all model invocations in the synchronous, non-streaming mode unless streaming is explicitly enabled through configuration. The non-streaming mode SHALL remain supported for every model provider (OpenAI, Ollama, LocalAI) exactly as before.

#### Scenario: No streaming option set anywhere
- **WHEN** `config.json` contains no streaming entry
- **AND** `analyse` is invoked without a streaming option
- **THEN** the run SHALL use the non-streaming mode for all model invocations

#### Scenario: Streaming explicitly disabled
- **WHEN** the effective streaming setting is `false`
- **THEN** the run SHALL use the non-streaming mode for all model invocations

### Requirement: Streaming mode streams the model response

When the streaming setting is enabled and the model provider is the OpenAI-compatible provider, each model invocation SHALL be sent with streaming enabled on the wire, and the response SHALL be received incrementally so that the connection stays active throughout generation rather than going silent until the full response is ready.

#### Scenario: Request is sent with streaming enabled
- **WHEN** the effective streaming setting is `true`
- **AND** the model provider is the OpenAI-compatible provider
- **THEN** each chat request SHALL be sent with the streaming flag enabled
- **AND** the response body SHALL be consumed as a stream of incremental updates

### Requirement: Streaming mode preserves the task-step response contract

Enabling streaming SHALL NOT change any observable task-step behavior compared to the non-streaming mode. Every model invocation SHALL surface to the task step as one complete response, and its downstream processing (tool execution, payload extraction, schema validation, token usage recording, interaction logging, retry handling) SHALL behave exactly as in the non-streaming mode.

#### Scenario: Tool calls still loop in streaming mode
- **WHEN** a model invocation in streaming mode requests tool execution
- **THEN** the tool SHALL be executed, its result appended to the conversation, and a follow-up model invocation SHALL be made, exactly as in the non-streaming mode

#### Scenario: Payload parsing and schema validation apply to the aggregated response
- **WHEN** a task runs in streaming mode
- **THEN** the extracted JSON payload SHALL be the payload of the fully aggregated response
- **AND** it SHALL be validated against the task's response schema with the same outcome as the non-streaming mode would produce for the same response

#### Scenario: Token usage is recorded as reported
- **WHEN** a task step completes in streaming mode
- **THEN** the input, output and total token counts recorded for that step SHALL be the counts reported for its model invocations, summed over its attempts as in the non-streaming mode

#### Scenario: Retriable model errors are handled identically
- **WHEN** a retriable model error occurs during a model invocation in streaming mode
- **THEN** the task step SHALL be treated as rejected with the error carried into the repair hint of the next attempt, exactly as in the non-streaming mode

#### Scenario: Interaction logging is unchanged
- **WHEN** a task step executes in streaming mode
- **THEN** the console and file interaction logs SHALL contain the same message sequence, token usage and metadata as the non-streaming mode produces for the same exchange

### Requirement: Streaming is restricted to the OpenAI-compatible provider

Streaming SHALL be available only for the OpenAI-compatible model provider. When the streaming setting is enabled while the model provider is any other provider, the run SHALL fail at startup, before any task step is executed.

#### Scenario: Unsupported provider with streaming enabled
- **WHEN** the effective streaming setting is `true`
- **AND** the model provider is Ollama or LocalAI
- **THEN** the run SHALL terminate before executing any task step
- **AND** it SHALL report an error stating that streaming is not supported for that provider
