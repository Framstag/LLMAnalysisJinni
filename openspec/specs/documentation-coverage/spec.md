# Documentation Coverage

## Purpose

Ensures that all active analysis evaluation properties produced by the analysis pipeline have corresponding rendering sections in the documentation template (`Documentation.adoc.hbs`). This prevents a gap where evaluation data exists in the analysis state but is never displayed in generated reports.

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

### Requirement: Modernisation anchors rendered in documentation

The system SHALL render the modernisation anchors in the generated documentation output, one table per anchor type,
naming the anchor, the element it points at, the evidence it rests on and the candidate patterns that fit it.

#### Scenario: Anchors are rendered per type

- **WHEN** the analysis state holds a modernisation anchor result
- **THEN** the documentation includes a section for the modernisation anchors with a table per anchor type, and each
  row names the anchor, its element, its evidence and its candidate patterns

#### Scenario: An anchor type without its input is rendered as absent

- **WHEN** the modernisation anchor result reports an anchor type as absent
- **THEN** the section names that anchor type as absent with its reason, instead of leaving the type out

#### Scenario: Section carries its pointer line

- **WHEN** the modernisation anchor section is rendered
- **THEN** it names the reference entry that explains the anchor types and the candidate patterns before its tables

### Requirement: Hotspot evaluation rendered in documentation

The system SHALL render the results of the hotspot evaluation in the generated documentation output, as a table of the
ranked classes with their change frequency and their complexity, and SHALL name the reference entry of the hotspot
analysis before that table.

#### Scenario: Hotspots are rendered

- **WHEN** the analysis state holds a hotspot evaluation
- **THEN** the documentation includes a section for the hotspots with one row per ranked class, and the section names
  the reference entry that explains the join before its table

#### Scenario: The absence of the history is rendered

- **WHEN** the hotspot evaluation reports the hotspots as absent
- **THEN** the section names the absence with its reason instead of rendering an empty table

### Requirement: Modernisation strategy rendered in documentation

The system SHALL render the result of the modernisation strategy analysis in the generated documentation output, as one
table per modernisation area, naming the area, the patterns that fit it, the evidence for and against them and the
figures the cost rests on, and SHALL name the reference entry of the strategy analysis before those tables.

#### Scenario: The areas are rendered

- **WHEN** the analysis state holds a modernisation strategy result
- **THEN** the documentation includes a section for the modernisation strategy with one table per area, and each row
  names the area, its patterns, the evidence and the figures of the cost

#### Scenario: The order is rendered as an order and not as a verdict

- **WHEN** the strategy section is rendered
- **THEN** it presents the areas in the order of the counted cost and names no area as the one to start with

#### Scenario: An area type without its evidence is rendered as absent

- **WHEN** the strategy result reports an area type as absent
- **THEN** the section names that area type as absent with its reason, instead of leaving the type out
