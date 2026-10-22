# Spec Delta

## MODIFIED Requirements

### Requirement: A further attempt is a fresh conversation with a repair hint

Every attempt SHALL build its conversation from scratch. The first attempt SHALL carry the task's messages and the schema description only. From the second attempt on, the user message SHALL additionally carry a repair hint placed after the schema description, and the rejected answer of the previous attempt SHALL NOT be included.

The hint SHALL name the reason the previous attempt was rejected and, for a schema violation, SHALL list the violation messages, which SHALL be bounded.

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

#### Scenario: The hint follows the schema description

- **WHEN** a step runs a further attempt
- **THEN** the repair hint SHALL be placed after the schema description in the user message

#### Scenario: A payload failure names the parser message

- **WHEN** a step runs a further attempt after a payload failure
- **THEN** the repair hint SHALL carry the parser's message
- **AND** it SHALL carry a bounded excerpt of the rejected payload
