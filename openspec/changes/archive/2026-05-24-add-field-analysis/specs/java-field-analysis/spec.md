## ADDED Requirements

### Requirement: Module field visibility distribution report

The system SHALL provide a tool that returns the distribution of field visibility levels and modifier flags across all classes in a given module.

The report MUST include separate distributions for production code, test code, and generated code. Each distribution maps visibility level (PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE) and modifier flags (STATIC, FINAL) to field counts.

#### Scenario: Successful field visibility report generation
- **WHEN** the LLM calls `java_get_field_visibility_report` with a module name
- **THEN** the system returns 3 `Distribution` objects — one each for production, test, and generated code — mapping visibility level strings and modifier flags to field counts

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_field_visibility_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Module class cohesion report

The system SHALL provide a tool that returns class cohesion metrics per module, including field count distribution, method-to-field ratio distribution, and field-access ratio distribution across all classes in a given module.

The report MUST include separate distributions for production code, test code, and generated code.

#### Scenario: Successful cohesion report generation
- **WHEN** the LLM calls `java_get_class_cohesion_report` with a module name
- **THEN** the system returns 3 groups of `Distribution` objects — one group (field count, method-to-field ratio, field-access ratio) each for production, test, and generated code

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_class_cohesion_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Field evaluation task

The system SHALL include an analysis task that evaluates the field visibility distribution per module and produces architecture findings with recommendations.

#### Scenario: Public field detected
- **WHEN** a module has public fields on domain classes (excluding constants marked static final)
- **THEN** the evaluation SHOULD flag these as encapsulation leaks

#### Scenario: Data class detected
- **WHEN** a class has all fields public and only getters/setters as methods
- **THEN** the evaluation SHOULD flag this as a potential data class / anemic domain model

#### Scenario: God class detected
- **WHEN** a class has many fields (greater than 10) and many methods (greater than 20)
- **THEN** the evaluation SHOULD flag this as a potential god class violation of Single Responsibility Principle

#### Scenario: Low cohesion detected
- **WHEN** a class has many fields compared to methods, or many methods with few accessing fields
- **THEN** the evaluation SHOULD flag this as potential low cohesion
