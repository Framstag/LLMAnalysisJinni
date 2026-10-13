# Documentation Coverage

## Purpose

Ensures that all active analysis evaluation properties produced by the analysis pipeline have corresponding rendering sections in the documentation template (`Documentation.md.hbs`). This prevents a gap where evaluation data exists in the analysis state but is never displayed in generated reports.

## Requirements

### Requirement: Visibility evaluation rendered in documentation

The system SHALL render the results of the visibility evaluation task in the generated documentation output.

#### Scenario: Module has visibility findings
- **WHEN** the module has `visibilityEvaluation` data in the analysis state
- **THEN** the documentation includes a section "Analysis of Method Visibility per Module" with a table of aspect/urgency/criticality/expectation/reasoning/finding/recommendation rows

### Requirement: Inheritance evaluation rendered in documentation

The system SHALL render the results of the inheritance evaluation task in the generated documentation output.

#### Scenario: Module has inheritance findings
- **WHEN** the module has `inheritanceEvaluation` data in the analysis state
- **THEN** the documentation includes a section "Analysis of Class Inheritance per Module" with a table of evaluation rows

### Requirement: Method complexity evaluation rendered in documentation

The system SHALL render the results of the method complexity evaluation task in the generated documentation output.

#### Scenario: Module has complexity findings
- **WHEN** the module has `methodComplexityEvaluation` data in the analysis state
- **THEN** the documentation includes a section "Analysis of Method Complexity per Module" with a table of evaluation rows

### Requirement: Every rendered evaluation section points to its reference entry

The generated report SHALL name the reference entry that explains an analysis in every section that renders the result of that analysis, so that a reader of a section can find its metric definition, weights and thresholds.

#### Scenario: Section carries a pointer line

- **WHEN** the report renders a section for a documented analysis
- **THEN** the section names the reference entry of that analysis before its evaluation tables

#### Scenario: Pointer names an existing entry

- **WHEN** a pointer line is read
- **THEN** the reference document holds an entry of the name the pointer gives

#### Scenario: Missing pointer is a coverage gap

- **WHEN** the report renders a section for a documented analysis that carries a result and the section has no pointer line
- **THEN** the report is incomplete, in the same way as a section missing for a stored result

#### Scenario: Section without a result still points

- **WHEN** a documented analysis produced no result in a run
- **THEN** its reference entry is still described in the reference chapter, and the section, if rendered, carries its pointer line

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
