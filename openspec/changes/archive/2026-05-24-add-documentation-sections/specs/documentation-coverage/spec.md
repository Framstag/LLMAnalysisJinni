## ADDED Requirements

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
