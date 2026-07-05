# Enforce Evaluation Object Format

## Purpose

Ensures that all batch evaluation tasks produce `evaluations[]` items as properly structured JSON objects rather than plain strings, enabling correct rendering in the documentation template.

## Requirements

### Requirement: Prompt enforces object format for evaluations

All batch evaluation prompts SHALL include an explicit instruction that each `evaluations[]` item MUST be a JSON object with fields `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`, and MUST NOT be a plain string.

#### Scenario: Prompt contains object format requirement

- **WHEN** a user inspects any `*_evaluation_all.md` prompt file
- **THEN** the Response Requirements section SHALL contain a bullet stating that `evaluations[]` items must be JSON objects with all required fields

#### Scenario: All 17 evaluation prompts are updated

- **WHEN** checking all files matching `analysis/software-architecture/prompts/*_evaluation_all.md`
- **THEN** each file SHALL contain the object format requirement in its Response Requirements section

### Requirement: Template only handles object format

The documentation template `Documentation.md.hbs` SHALL render `evaluations[]` items assuming they are JSON objects with all required fields. It SHALL NOT contain fallback branches for string or partial-object formats.

#### Scenario: Template has no fallback branches

- **WHEN** inspecting `analysis/software-architecture/documentation/Documentation.md.hbs`
- **THEN** the template SHALL NOT contain `{{#if aspect}}` or `{{#if finding}}` conditional branches inside evaluation table row rendering

#### Scenario: Table row renders all columns from object fields

- **WHEN** the template renders an `evaluations[]` item
- **THEN** it SHALL produce a single table row with all 7 columns: `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`

#### Scenario: Empty cells indicate malformed data

- **WHEN** an `evaluations[]` item is a string or missing required fields
- **THEN** the rendered table row SHALL have empty cells for missing fields, making the data quality problem immediately visible
