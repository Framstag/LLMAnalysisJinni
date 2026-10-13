# Spec Delta

## ADDED Requirements

### Requirement: Context map rendered in documentation

The system SHALL render the context map in the generated documentation output.

#### Scenario: Context candidates are rendered

- **WHEN** the analysis state holds a context map result
- **THEN** the documentation includes a context map section naming each context candidate with the modules it groups

#### Scenario: The two axes of a relation are rendered

- **WHEN** the context map section is rendered and the result names relations between contexts
- **THEN** the section holds a table with one row per relation, naming both contexts, the relation, the shared language and the structural coupling, and the counted evidence the relation rests on

#### Scenario: The pair-level quadrant stays in the evidence

- **WHEN** a reader wants the import count and the lexical overlap of a concrete pair of modules
- **THEN** the evidence CSV of the quadrant holds every pair with both values, and the reference entry names that file

#### Scenario: Section carries its pointer line

- **WHEN** the context map section is rendered
- **THEN** it names the reference entry that explains the quadrant and the intrusion measure before its tables

#### Scenario: Unplaced modules are named

- **WHEN** the context map result names modules it could not place
- **THEN** the section names them with the reason instead of leaving them out
