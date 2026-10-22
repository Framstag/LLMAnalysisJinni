# Spec Delta

## MODIFIED Requirements

### Requirement: Module method complexity distribution report

The system SHALL provide a tool that returns method complexity metrics per module, including parameter count distribution (per method) and method lines of code distribution (per method, computed as AST body statement count).

The report MUST include separate distributions for production, test, and generated code. Lines of code is the count of statements in the method body (AST nodes), not source lines. Parameter count is the number of formal parameters in the method declaration.

#### Scenario: Successful complexity report generation

- **WHEN** the LLM calls `java_get_method_complexity_report` with a module name
- **THEN** the system returns 3 pairs of `Distribution` objects - one pair (parameter count + lines of code) each for production, test, and generated code

#### Scenario: Module not yet analyzed

- **WHEN** the LLM calls `java_get_method_complexity_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Method with no body parsed from class file

- **WHEN** the LLM calls `java_get_method_complexity_report` and a method was parsed from a class file only (no source)
- **THEN** that method's linesOfCode is null and excluded from the LoC distribution for that module

#### Scenario: Zero-parameter methods

- **WHEN** a module contains methods with no parameters
- **THEN** the parameter count distribution includes count 0 for those methods
