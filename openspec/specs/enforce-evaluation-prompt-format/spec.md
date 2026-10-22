# Enforce Evaluation Prompt Format

## Purpose

Ensures that the evaluation prompts require `evaluations[]` items as JSON objects with all required fields rather than plain strings, so that the documentation template can render them.

## Requirements

### Requirement: Prompt enforces object format for evaluations

All batch evaluation prompts SHALL include an explicit instruction that each `evaluations[]` item MUST be a JSON object with fields `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`, and MUST NOT be a plain string.

#### Scenario: Prompt contains object format requirement

- **WHEN** a user inspects any `*_evaluation_all.md` prompt file
- **THEN** the Response Requirements section SHALL contain a bullet stating that `evaluations[]` items must be JSON objects with all required fields

#### Scenario: Every evaluation prompt states the format

- **WHEN** checking all files matching `analysis/software-architecture/prompts/*_evaluation_all.md`
- **THEN** each file SHALL contain the object format requirement in its Response Requirements section
