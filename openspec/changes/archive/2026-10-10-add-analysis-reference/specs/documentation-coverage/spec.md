# Spec Delta

## ADDED Requirements

### Requirement: Every rendered evaluation section points to its reference entry

The generated report SHALL name the reference entry that explains an analysis in every section that renders the
result of that analysis, so that a reader of a section can find its metric definition, weights and thresholds.

#### Scenario: Section carries a pointer line

- **WHEN** the report renders a section for a documented analysis
- **THEN** the section names the reference entry of that analysis before its evaluation tables

#### Scenario: Pointer names an existing entry

- **WHEN** a pointer line is read
- **THEN** the reference document holds an entry of the name the pointer gives

#### Scenario: Missing pointer is a coverage gap

- **WHEN** the report renders a section for a documented analysis that carries a result and the section has no
  pointer line
- **THEN** the report is incomplete, in the same way as a section missing for a stored result

#### Scenario: Section without a result still points

- **WHEN** a documented analysis produced no result in a run
- **THEN** its reference entry is still described in the reference chapter, and the section, if rendered, carries
  its pointer line
