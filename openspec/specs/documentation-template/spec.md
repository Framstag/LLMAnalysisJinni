# Documentation Template

## Purpose

Defines how evaluation results are rendered in the generated documentation output (`Documentation.adoc`). The template uses Handlebars to transform analysis state into a structured AsciiDoc report.

## Requirements

### Requirement: Evaluation table rows render from object fields

The system SHALL render evaluation results in per-module tables with columns: Aspect, Urgency, Criticality, Expectation, Reasoning, Finding, Recommendation.

The template assumes all `evaluations[]` items are JSON objects with all 7 required fields. No fallback branches for string or partial-object formats. Missing fields render as empty cells.

#### Scenario: Template renders full table row from object

- **WHEN** the template encounters an `evaluations[]` item that is a JSON object with all required fields
- **THEN** it renders a single table row with all 7 columns populated from the object fields

#### Scenario: Template has no conditional fallbacks

- **WHEN** inspecting `analysis/software-architecture/documentation/Documentation.adoc.hbs`
- **THEN** the evaluation table row rendering SHALL NOT contain `{{#if aspect}}` or `{{#if finding}}` conditional branches

#### Scenario: Malformed data produces empty cells

- **WHEN** an `evaluations[]` item is a string or missing required fields
- **THEN** the rendered cells for missing fields SHALL be empty (not dashes or fallback text)

### Requirement: Document generation supports selecting an alternate template by name

The document command SHALL accept an optional template name identifying which `.hbs` template under the `documentation/` directory of the working directory to render, in addition to its existing required working-directory argument. The name is given without the `.hbs` suffix, and the selected template SHALL be rendered against the same analysis state and into the same output file as the default.

#### Scenario: Template name explicitly supplied

- **WHEN** the document command is invoked with a template name of `Documentation_German.adoc`
- **THEN** the document is rendered from `documentation/Documentation_German.adoc.hbs` using the same analysis state as without the option

#### Scenario: Template name omitted preserves existing behavior

- **WHEN** the document command is invoked without a template name
- **THEN** the document is rendered from `documentation/Documentation.adoc.hbs`, exactly as it was before this requirement existed
