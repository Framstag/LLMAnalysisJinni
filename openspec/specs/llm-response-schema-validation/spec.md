# LLM Response Schema Validation

## Purpose

After the LLM produces a JSON response, validate it against the task's declared JSON schema. Violations are logged as warnings for diagnosis — the response is still accepted to avoid breaking the pipeline.

## Requirements

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

### Requirement: Schema compatibility

The validation MUST work with the existing JSON Schema Draft 2020-12 schema files in `analysis/<domain>/results/`. Schema serialization MUST use string-based API (`SchemaRegistry.getSchema(String, InputFormat)`) to avoid `tools.jackson`/`com.fasterxml.jackson` type incompatibility.

#### Scenario: Schema loaded via string API
- **WHEN** the `responseSchema` JsonNode is serialized to a JSON string
- **THEN** `SchemaRegistry.getSchema(schemaString, InputFormat.JSON)` creates the validator schema

### Requirement: Schema text description always appended

The schema text description SHALL be appended to the user message regardless of `nativeJSON` mode. In native JSON mode the LLM receives both the formal `responseFormat` parameter AND the verbal schema description.

#### Scenario: Dual cue for native JSON mode
- **WHEN** `nativeJSON=true` and the last message is a `UserMessage`
- **THEN** the schema text description is appended to that message
- **AND** the `responseFormat` parameter also carries the formal schema

#### Scenario: Verbal cue for non-native JSON mode
- **WHEN** `nativeJSON=false` and the last message is a `UserMessage`
- **THEN** the schema text description is appended to that message
- **AND** the `responseFormat` is set to TEXT

### Requirement: A violation report identifies where and what was rejected

Every reported schema violation SHALL identify the position of the offending value inside the payload
and the offending value itself, or state that a required value is missing. An enumeration violation
SHALL name the permitted values. The report SHALL NOT depend on the default locale of the machine the
engine runs on, so that the same payload yields the same report everywhere.

#### Scenario: Enumeration violation
- **WHEN** a payload contains an enumerated property whose value is not one of the permitted values
- **THEN** the report SHALL name the position of that property in the payload
- **AND** the report SHALL name the rejected value
- **AND** the report SHALL name the permitted values

#### Scenario: Missing required property
- **WHEN** a payload lacks a property the schema requires
- **THEN** the report SHALL name the property
- **AND** the report SHALL name the position of the object that lacks it

#### Scenario: Locale independence
- **WHEN** the same payload is validated on a machine whose default locale is German and on one whose
  default locale is English
- **THEN** the two reports SHALL be equivalent

#### Scenario: The report stays visible during the run
- **WHEN** a response is rejected for schema violations
- **THEN** each violation SHALL be reported through the engine's logging path
- **AND** the report SHALL NOT be written directly to the terminal

### Requirement: The repair hint is bounded

The violations carried into the next attempt of the same step SHALL be bounded in number and in
characters. When the bound carries less than every violation, the hint SHALL state the total number of
violations, so that the model can tell a single wrong field from a response of the wrong shape.

#### Scenario: Many violations
- **WHEN** a response violates the schema in more places than the hint carries
- **THEN** the hint SHALL carry at most the bound
- **AND** the hint SHALL state how many violations the response had in total

#### Scenario: Long violation text
- **WHEN** a violation's text is longer than the bound allows
- **THEN** the hint SHALL stay within its character bound
- **AND** the part it carries SHALL remain readable

#### Scenario: The complete list remains available
- **WHEN** a response is rejected for schema violations
- **THEN** the complete violation list SHALL be available for the diagnosis outside the prompt

#### Scenario: Few violations are carried completely
- **WHEN** a response has fewer violations than the bound carries
- **THEN** the hint SHALL carry all of them

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
