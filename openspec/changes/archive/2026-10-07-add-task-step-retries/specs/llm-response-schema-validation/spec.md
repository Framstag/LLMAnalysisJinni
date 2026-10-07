# Spec Delta

## MODIFIED Requirements

### Requirement: Post-hoc validation

Every LLM response that is successfully parsed as JSON MUST be validated against the `jsonResponseSchema` that was used to instruct the LLM. The validation MUST use the networknt `JsonSchema` validator with Draft 2020-12 dialect. The outcome of the validation SHALL decide whether the response is accepted or reported as a retryable step failure.

#### Scenario: Conformant response passes validation
- **WHEN** the LLM returns a JSON response that matches all schema constraints (type, required fields, enum values)
- **THEN** the response is accepted with no warning logged

#### Scenario: Non-conformant response logs warning
- **WHEN** the LLM returns a JSON response that violates schema constraints (e.g., missing required field, wrong type, invalid enum)
- **THEN** a WARN-level log entry is emitted with the number of violations and each violation's error message

#### Scenario: Validation decides the step outcome
- **WHEN** the validation found at least one violation
- **THEN** the response SHALL be reported to the retry handling as a retryable failure
- **AND** the response SHALL NOT be accepted as the result of the step

## ADDED Requirements

### Requirement: Validation gates what a step publishes

A response that conforms to the declared schema SHALL be accepted and SHALL be the response the step publishes. A response that violates the declared schema SHALL NOT be published. The violation messages SHALL be made available to the retry handling so that a further attempt can be told what was wrong.

#### Scenario: Conformant response is published
- **WHEN** the validation found no violation
- **THEN** the response SHALL be returned to the caller and stored in `analysisState`

#### Scenario: Violation blocks publication
- **WHEN** a schema violation is detected and no further attempt is available
- **THEN** the response SHALL NOT be stored in `analysisState`
- **AND** the step SHALL be reported as failed

#### Scenario: Violations are reported to the retry handling
- **WHEN** a schema violation is detected
- **THEN** the number of violations and their messages SHALL be reported to the retry handling
- **AND** the message text SHALL be bounded

## REMOVED Requirements

### Requirement: Diagnostic-only validation

**Reason**: A violation is no longer inert. It now ends the step when the attempt budget is exhausted, and it suppresses publication of the non-conformant payload, so the requirement that the response is stored regardless of the validation outcome contradicts the retry behaviour.
**Migration**: Validation now determines the step outcome. A conformant response is stored as before; a non-conformant response is reported as a retryable failure and is not stored. The replacement requirements live in this capability and in `llm-response-retry`.

### Requirement: Non-interference

**Reason**: This requirement names retry behaviour and task status tracking as things validation must not affect, which is exactly what validation now drives.
**Migration**: Validation affects the number of attempts, the task status and the response properties that are written. Dependency resolution is still unaffected by validation itself: it follows the task status as before, and a step whose attempts were all rejected is marked failed without unlocking its dependents.
