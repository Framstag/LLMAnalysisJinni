# Spec Delta

## ADDED Requirements

### Requirement: Domain language evaluation rendered in documentation

The system SHALL render the results of the domain vocabulary and domain language evaluation in the generated documentation output.

#### Scenario: Module has domain language findings

- **WHEN** the analysis state holds domain language findings for a module
- **THEN** the documentation includes a section for the domain language of the modules with a table carrying the aspect, urgency, criticality, expectation, reasoning, finding and recommendation of each finding

#### Scenario: Section carries its pointer line

- **WHEN** the domain language section is rendered
- **THEN** it names the reference entry that explains the domain vocabulary metrics before its tables

#### Scenario: The project glossary is rendered once

- **WHEN** the analysis state holds the project glossary
- **THEN** the documentation renders the glossary as part of the domain language chapter instead of only naming it
