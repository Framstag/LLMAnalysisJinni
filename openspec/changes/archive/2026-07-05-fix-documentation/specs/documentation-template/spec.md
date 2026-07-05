## MODIFIED Requirements

### Requirement: Evaluation table rows render from object fields

The system SHALL render evaluation results in per-module tables with columns: Aspect, Urgency, Criticality, Expectation, Reasoning, Finding, Recommendation.

**Previous behavior:** Template included fallback branches (`{{#if aspect}}`, `{{#if finding}}`) to handle string or partial-object `evaluations[]` items, rendering dashes for missing fields.

**New behavior:** Template assumes all `evaluations[]` items are JSON objects with all 7 required fields. No fallback branches. Missing fields render as empty cells.

#### Scenario: Template renders full table row from object

- **WHEN** the template encounters an `evaluations[]` item that is a JSON object with all required fields
- **THEN** it renders a single table row with all 7 columns populated from the object fields

#### Scenario: Template has no conditional fallbacks

- **WHEN** inspecting the template source
- **THEN** the evaluation table row rendering SHALL NOT contain `{{#if aspect}}` or `{{#if finding}}` conditional branches

#### Scenario: Malformed data produces empty cells

- **WHEN** an `evaluations[]` item is a string or missing required fields
- **THEN** the rendered cells for missing fields SHALL be empty (not dashes or fallback text)
