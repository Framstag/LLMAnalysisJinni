## ADDED Requirements

### Requirement: Data class detection report

The system SHALL provide a tool that identifies data class candidates in a given module. A class is flagged as a data class candidate if all its fields are private and all its methods are getters, setters, or constructors; or all its fields are public.

The report MUST include separate lists for production code, test code, and generated code.

#### Scenario: Successful data class detection
- **WHEN** the LLM calls `java_get_data_class_report` with a module name
- **THEN** the system returns 3 lists of class names (prod/test/gen) identified as data class candidates

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_data_class_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Data class evaluation task

The system SHALL include an analysis task that evaluates data class candidates per module.

#### Scenario: Data class detected
- **WHEN** a module has classes flagged as data class candidates
- **THEN** the evaluation SHOULD flag these as potential anemic domain models
