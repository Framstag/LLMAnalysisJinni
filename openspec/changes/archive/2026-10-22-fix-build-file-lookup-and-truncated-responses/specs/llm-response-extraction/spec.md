# Spec Delta

## MODIFIED Requirements

### Requirement: Missing payload and invalid payload are distinguished

The system SHALL distinguish a response in which no payload was located from a response in which a
payload was located but does not parse, and SHALL report the two conditions differently. It SHALL
also distinguish a response that the model stopped in the middle of from a response the model
finished, and SHALL report the truncated response as its own condition rather than as a payload
that does not parse.

#### Scenario: Located payload does not parse
- **WHEN** a located payload is syntactically invalid JSON
- **THEN** the run SHALL report a parse failure for a located payload
- **AND** the affected task SHALL NOT be treated as having produced a payload

#### Scenario: No payload located
- **WHEN** a response contains no bracketed JSON payload at all
- **THEN** the run SHALL report the missing payload, not a parse failure of located content

#### Scenario: A response the model stopped in the middle of
- **WHEN** the model reports that it did not finish its answer
- **THEN** the run SHALL report that the response was truncated
- **AND** it SHALL NOT report the condition as a payload that does not parse
- **AND** the affected task SHALL NOT be treated as having produced a payload

#### Scenario: The report names the reason the model stopped
- **WHEN** the run reports that a response was truncated
- **THEN** the report SHALL name the reason the model gave for stopping

### Requirement: A response without a locatable payload is reported as such

When no payload can be located in a response, the system SHALL report that the response contains no
JSON payload, SHALL NOT report a repair that did not take place, and SHALL NOT write the complete
response body to the console for this condition. The result reported for the task SHALL name the
missing payload rather than a parser message about the first unrelated character.

#### Scenario: Response without any payload
- **WHEN** a model response contains prose only
- **THEN** the run SHALL report that no JSON payload was found in that response
- **AND** the report SHALL NOT claim that the response was corrected
- **AND** the complete response body SHALL NOT be written to the console

#### Scenario: Truncated payload
- **WHEN** a model response opens a code fence or an object but the payload is cut off before its closing bracket
- **THEN** the run SHALL report that no complete JSON payload was found
- **AND** the affected task SHALL NOT be treated as having produced a payload

#### Scenario: Failure is attributed to the missing payload
- **WHEN** a task's response contains no locatable payload
- **THEN** the failure reported for that task SHALL state that no JSON payload was found in the response

## ADDED Requirements

### Requirement: A truncated response is reported as truncated and not as a missing payload

The system SHALL report a response that the model stopped in the middle of as truncated, and SHALL
NOT report it as a response without a payload.

#### Scenario: A truncated response is not reported as a missing payload
- **WHEN** the model reports that it did not finish its answer
- **THEN** the failure reported for the task SHALL state that the response was truncated
- **AND** it SHALL NOT state that no JSON payload was found

### Requirement: The reasoning of the model is kept for the diagnosis of a step

The system SHALL keep the reasoning the model wrote for a step available in the chat log of that
step, so that a reader of a failed step can see why the model asked for what it asked for.

#### Scenario: The reasoning of a failed step is in its log
- **WHEN** a step failed
- **THEN** the chat log of that step contains the reasoning the model wrote for it

#### Scenario: The reasoning is not part of the answer
- **WHEN** the reasoning of a step is kept
- **THEN** it is not part of the payload the step produced
