## ADDED Requirements

### Requirement: Method count distribution report

The system SHALL provide a tool that returns the distribution of method count per class across all classes in a given module.

The report MUST include separate distributions for production code, test code, and generated code.

#### Scenario: Successful method count report generation
- **WHEN** the LLM calls `java_get_method_count_report` with a module name
- **THEN** the system returns 3 `Distribution` objects (prod/test/gen) mapping method counts to class counts

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_method_count_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Method count evaluation task

The system SHALL include an analysis task that evaluates method count distribution and flags classes with excessive methods.

#### Scenario: God class candidate detected
- **WHEN** a class has more than 20 methods
- **THEN** the evaluation SHOULD flag this as a potential god class candidate
