## ADDED Requirements

### Requirement: Module method complexity distribution report

The system SHALL provide a tool that returns method complexity metrics per module, including parameter count distribution (per method) and method lines of code distribution (per method, computed as AST body statement count).

The report MUST include separate distributions for production code, test code, and generated code. Lines of code is measured as the count of statements in the method body (AST nodes), not source lines. Parameter count is the number of formal parameters in the method declaration.

#### Scenario: Successful complexity report generation
- **WHEN** the LLM calls `java_get_method_complexity_report` with a module name
- **THEN** the system returns 3 pairs of `Distribution` objects — one pair (parameter count + lines of code) each for production, test, and generated code

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_method_complexity_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Method with no body parsed from .class file
- **WHEN** the LLM calls `java_get_method_complexity_report` and a method was parsed from a .class file only (no source)
- **THEN** that method's linesOfCode is null and excluded from the LoC distribution for that module

#### Scenario: Zero-parameter methods
- **WHEN** a module contains methods with no parameters
- **THEN** the parameter count distribution includes count 0 for those methods

### Requirement: Method complexity evaluation task

The system SHALL include an analysis task that evaluates method complexity metrics per module (cross-referenced with cyclomatic complexity) and produces architecture findings with recommendations.

#### Scenario: Excessive parameter count detected
- **WHEN** a module has methods with > 7 parameters
- **THEN** the evaluation SHOULD flag these as excessive parameter list smells

#### Scenario: Long method detected
- **WHEN** a module has methods with > 30 AST statements
- **THEN** the evaluation SHOULD flag these as potential god methods

#### Scenario: Multi-dimensional complexity correlation
- **WHEN** a method has high cyclomatic complexity (> 10), high parameter count (> 5), and high lines of code (> 30)
- **THEN** the evaluation SHOULD flag this as a high-priority refactoring target

#### Scenario: Method length as readability proxy
- **WHEN** a method has low cyclomatic complexity (< 5) but high lines of code (> 40)
- **THEN** the evaluation SHOULD note that the method may be doing many sequential operations without branching