# llm-response-retry Specification

## Purpose
Retries a task step inside a run when the model did not deliver a usable answer, so that a transient or self-correctable failure does not end a task until the run is over. The capability defines the per-step attempt budget and its configuration, which outcomes earn another attempt, what a further attempt is told, which attempt's payload is published, and how the retries are reported in the display and the logs.

## Requirements

### Requirement: Retry applies to one task step

A retry SHALL be applied to a single task step. A step is one execution of a non-loop task, or the execution of one loop index of a loop task. Loop indices SHALL be attempted independently, and an index whose response was accepted SHALL NOT be attempted again in the same run.

#### Scenario: Non-loop task step is retried
- **WHEN** a non-loop task's attempt fails in a retryable way
- **AND** the attempt budget is not yet exhausted
- **THEN** that task SHALL be attempted again

#### Scenario: Loop index is retried independently
- **WHEN** one index of a loop task fails in a retryable way
- **AND** the attempt budget is not yet exhausted
- **THEN** only that index SHALL be attempted again
- **AND** the other indices of the task SHALL be unaffected

#### Scenario: Accepted loop index is not repeated
- **WHEN** one index of a loop task produced an accepted response
- **THEN** that index SHALL NOT be attempted again in the same run
- **AND** it SHALL be recorded as successful

### Requirement: The attempt budget is a workspace configuration option

The maximum number of attempts of one task step SHALL be read from the workspace configuration under the key `retries`, and SHALL default to 3 when the configuration does not provide it. The value SHALL mean the maximum number of attempts, so a value of 3 allows one initial attempt and two further attempts. A value below 1 SHALL be rejected as a configuration error instead of being clamped.

#### Scenario: Default budget
- **WHEN** the workspace configuration does not contain `retries`
- **AND** a step keeps failing in a retryable way
- **THEN** the step SHALL be attempted three times in total

#### Scenario: Configured budget
- **WHEN** the workspace configuration contains `retries: 5`
- **AND** a step keeps failing in a retryable way
- **THEN** the step SHALL be attempted five times in total

#### Scenario: Invalid budget is rejected
- **WHEN** the workspace configuration contains `retries: 0`
- **THEN** the run SHALL report a configuration error for that setting

### Requirement: Outcomes that earn another attempt

A step SHALL be attempted again when the model returned no response text, when the response carries no locatable JSON payload, when the located payload does not parse, when the payload violates the declared response schema, or when the model call raised a retriable error such as a timeout, a rate limit or an internal server error.

#### Scenario: Model returned no response text
- **WHEN** the model's answer carries no text at all
- **THEN** the step SHALL be attempted again

#### Scenario: Response carries no payload
- **WHEN** the model answered with text that contains no locatable JSON payload
- **THEN** the step SHALL be attempted again

#### Scenario: Located payload does not parse
- **WHEN** a JSON payload was located in the response but does not parse
- **THEN** the step SHALL be attempted again

#### Scenario: Payload violates the response schema
- **WHEN** the model returned a parsable payload that violates the declared response schema
- **THEN** the step SHALL be attempted again

#### Scenario: Retriable model error
- **WHEN** the model call raised a retriable error
- **THEN** the step SHALL be attempted again

### Requirement: Outcomes that end the step immediately

A step SHALL NOT be attempted again when the model call raised a non-retriable error, when the model does not support the requested feature, when a tool call failed in a way that aborts the step, or when the engine failed for a reason that another attempt cannot repair, such as a chat log that cannot be written. The step SHALL be marked failed without spending a further attempt.

#### Scenario: Non-retriable model error
- **WHEN** the model call raised a non-retriable error, such as an authentication failure, an invalid request, an unknown model or an unresolvable model server
- **THEN** the step SHALL be marked failed
- **AND** no further attempt SHALL be made

#### Scenario: Engine failure that another attempt cannot repair
- **WHEN** the step failed because the chat log could not be written
- **THEN** the step SHALL be marked failed
- **AND** no further attempt SHALL be made

### Requirement: A further attempt is a fresh conversation with a repair hint

Every attempt SHALL build its conversation from scratch. The first attempt SHALL carry the task's messages and the schema description only. From the second attempt on, the user message SHALL additionally carry a repair hint placed after the schema description, and the rejected answer of the previous attempt SHALL NOT be included. The hint SHALL name the reason the previous attempt was rejected and, for a schema violation, SHALL list the violation messages, which SHALL be bounded. For a payload failure the hint SHALL carry the parser's message and a bounded excerpt.

#### Scenario: First attempt carries no hint
- **WHEN** a step runs its first attempt
- **THEN** the user message SHALL carry the task's prompt and the schema description
- **AND** it SHALL carry no repair hint

#### Scenario: Retry carries the reason
- **WHEN** a step runs a further attempt after a rejected payload
- **THEN** the user message SHALL carry a repair hint naming the reason for the rejection

#### Scenario: Retry names the schema violations
- **WHEN** a step runs a further attempt after a schema violation
- **THEN** the repair hint SHALL list the violation messages
- **AND** the listed text SHALL be bounded

#### Scenario: Rejected answer is not echoed back
- **WHEN** a step runs a further attempt
- **THEN** the conversation SHALL contain the task's messages, the schema description and the repair hint
- **AND** it SHALL NOT contain the rejected answer of the previous attempt

### Requirement: Only an accepted attempt is published

A step SHALL be treated as successful, and its response property SHALL be written to the analysis results, only when an attempt produced a payload that conforms to the declared response schema. When every attempt of a step has been used without a conformant payload, the step SHALL be marked failed and no response property SHALL be written for it, including when the last attempt produced a parsable but non-conformant payload. The rejected payloads SHALL remain available only in the chat logs.

#### Scenario: Retry succeeds
- **WHEN** a step's first attempt is rejected and a further attempt produces a conformant payload
- **THEN** the step SHALL be marked successful
- **AND** the conformant payload SHALL be written to the response property of the step

#### Scenario: Budget exhausted
- **WHEN** every attempt of a step has been used without a conformant payload
- **THEN** the step SHALL be marked failed
- **AND** no response property SHALL be written for the step

#### Scenario: Non-conformant but parsable last attempt
- **WHEN** the last allowed attempt produced a parsable payload that violates the declared response schema
- **THEN** the step SHALL be marked failed
- **AND** that payload SHALL NOT be written to the analysis results

### Requirement: Retries are reported in the display and the engine log

Every retry SHALL be reported: the TUI task and worker rows SHALL show the attempt that is running against the attempt budget, the piped output SHALL print one line per retry naming the step and the reason, and the engine SHALL emit one WARN record per retry carrying the bounded reason. The reporting SHALL NOT depend on the display mode.

#### Scenario: TUI shows the attempt
- **WHEN** a retry runs while the TUI is the display mode
- **THEN** the row of the task or of the affected loop index SHALL show the current attempt against the budget

#### Scenario: Piped output shows the retry
- **WHEN** a retry runs while the piped output is the display mode
- **THEN** one line SHALL be printed naming the step, the reason and the attempt

#### Scenario: Engine log record per retry
- **WHEN** a step is attempted again
- **THEN** a WARN record SHALL be emitted with the task identifier and the bounded reason

### Requirement: Every attempt keeps its own chat log

Each attempt SHALL write its own chat log file, so the transcript of a rejected attempt is not overwritten by a later attempt. The first attempt of a step SHALL keep the existing log file name, and each further attempt SHALL add its attempt number to the file name. A later run of the same step SHALL overwrite the files of the attempts it repeats.

#### Scenario: Log of a rejected attempt survives
- **WHEN** a step's first attempt is rejected and a further attempt is written
- **THEN** the chat log file of the first attempt SHALL still exist
- **AND** the further attempt SHALL be written to a file whose name carries its attempt number

#### Scenario: Re-running a step overwrites its attempt files
- **WHEN** a step is executed again in a later run
- **THEN** the chat log files of that step's attempts SHALL be overwritten by the new run
