# LLM Response Schema Validation

## Overview

After the LLM produces a JSON response, validate it against the task's declared JSON schema. Violations are logged as warnings for diagnosis — the response is still accepted to avoid breaking the pipeline.

## Requirements

### Requirement: Post-hoc validation

Every LLM response that is successfully parsed as JSON MUST be validated against the `jsonResponseSchema` that was used to instruct the LLM. The validation MUST use the networknt `JsonSchema` validator with Draft 2020-12 dialect.

#### Scenario: Conformant response passes validation
- **WHEN** the LLM returns a JSON response that matches all schema constraints (type, required fields, enum values)
- **THEN** the response is accepted with no warning logged

#### Scenario: Non-conformant response logs warning
- **WHEN** the LLM returns a JSON response that violates schema constraints (e.g., missing required field, wrong type, invalid enum)
- **THEN** a WARN-level log entry is emitted with the number of violations and each violation's error message

### Requirement: Diagnostic-only validation

Schema violations MUST NOT cause the pipeline to fail or reject the response. The LLM response SHALL be stored in `analysisState` regardless of schema validation outcome. Validation MUST be diagnostic-only.

#### Scenario: Violation does not block storage
- **WHEN** a schema violation is detected
- **THEN** the response is still returned to the caller and stored in `analysisState`

### Requirement: Schema compatibility

The validation MUST work with the existing JSON Schema Draft 2020-12 schema files in `analysis/<domain>/results/`. Schema serialization MUST use string-based API (`SchemaRegistry.getSchema(String, InputFormat)`) to avoid `tools.jackson`/`com.fasterxml.jackson` type incompatibility.

#### Scenario: Schema loaded via string API
- **WHEN** the `responseSchema` JsonNode is serialized to a JSON string
- **THEN** `SchemaRegistry.getSchema(schemaString, InputFormat.JSON)` creates the validator schema

### Requirement: Non-interference

Validation MUST NOT affect:
- The response that gets stored in `analysisState`
- Task execution flow or dependency resolution
- Retry behavior or task status tracking

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