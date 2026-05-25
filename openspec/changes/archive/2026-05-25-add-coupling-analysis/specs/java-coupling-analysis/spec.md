## ADDED Requirements

### Requirement: Module coupling distribution report

The system SHALL provide a tool that returns the distribution of per-class efferent coupling (Ce) across all classes in a given module.

The report MUST include separate distributions for production code, test code, and generated code. Efferent coupling (Ce) is defined as the count of unique external types referenced by a class, excluding types in the same package. Coupling is computed from the class's file-level imports by filtering out same-package types.

#### Scenario: Successful coupling report generation
- **WHEN** the LLM calls `java_get_coupling_report` with a module name
- **THEN** the system returns 3 `Distribution` objects for class-level coupling (prod/test/gen) plus additional `Distribution` objects for module-level dependency counts

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_coupling_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Coupling evaluation task

The system SHALL include an analysis task that evaluates the coupling distribution per module and produces architecture findings with recommendations.

#### Scenario: Hub class detected
- **WHEN** a class has efferent coupling greater than 20
- **THEN** the evaluation SHOULD flag this as a potential hub class

#### Scenario: Highly coupled module detected
- **WHEN** a module has mean efferent coupling greater than 10 across production classes
- **THEN** the evaluation SHOULD flag this module as potentially too tightly coupled

#### Scenario: Dependency concentration on external module
- **WHEN** more than 50% of a module's imports resolve to a single external module
- **THEN** the evaluation SHOULD note this as potential framework lock-in or tight coupling to a specific dependency
