# Method Visibility Analysis

## Purpose

Provides tools and evaluation tasks for analyzing the distribution of method visibility levels (PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE) and modifier flags (static, final) across production, test, and generated code in a module. Enables detection of encapsulation leaks, anemic domain models, and procedural-style design.

## Requirements

### Requirement: Module method visibility distribution report

The system SHALL provide a tool that returns the distribution of method visibility levels (PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE) and modifier flags (static, final) across all production and test classes in a given module.

The report MUST include separate distributions for production code, test code, and generated code.

#### Scenario: Successful visibility report generation
- **WHEN** the LLM calls `java_get_visibility_distribution_report` with a module name
- **THEN** the system returns 3 `Distribution` objects - one each for production, test, and generated code - mapping visibility level strings to method counts

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_visibility_distribution_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Module with no code
- **WHEN** the LLM calls `java_get_visibility_distribution_report` with a module name that has analyzed classes but zero methods
- **THEN** the system returns 3 empty `Distribution` objects

### Requirement: Visibility evaluation task

The system SHALL include an analysis task that evaluates the method visibility distribution per module and produces architecture findings with recommendations.

#### Scenario: Encapsulation leak detected
- **WHEN** a module has more than 50% public methods across all classes (excluding test code)
- **THEN** the evaluation SHOULD flag this as a potential encapsulation concern

#### Scenario: Anemic domain model detected
- **WHEN** a module has more than 80% public methods with no private or protected methods on domain classes (excluding getters/setters, builders)
- **THEN** the evaluation SHOULD flag this as a possible anemic domain model

#### Scenario: Static method overuse
- **WHEN** a module has more than 40% static methods in production code
- **THEN** the evaluation SHOULD flag this as potential procedural-style design
