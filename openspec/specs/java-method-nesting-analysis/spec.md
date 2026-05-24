# Method Nesting Depth Analysis

## Purpose

Provides tools and evaluation tasks for analyzing method nesting depth distribution - the maximum scoping depth of control structures (if/for/while/do-while/switch/try/for-each) - across production, test, and generated code in a module. Enables the LLM to distinguish between "flat but complex" methods and "nested spaghetti" methods by cross-referencing nesting depth with cyclomatic complexity.

## Requirements

### Requirement: Module method nesting depth distribution report

The system SHALL provide a tool that returns the distribution of method nesting depth (max scoping depth of control structures) across all classes in a given module.

The report MUST include separate distributions for production code, test code, and generated code. Nesting depth is computed as the maximum level of nested if/for/while/do-while/switch/try/for-each blocks in the method body. Single-statement blocks without braces do not increase depth. Depth 0 means no control structures or all at top level.

Methods parsed from .class files (bytecode) have nesting depth 0 since bytecode has no scoping structure.

#### Scenario: Successful nesting depth report generation
- **WHEN** the LLM calls `java_get_method_nesting_depth_report` with a module name
- **THEN** the system returns 3 `Distribution` objects - one each for production, test, and generated code - mapping nesting depth integers to method counts

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_method_nesting_depth_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Module with no code
- **WHEN** the LLM calls `java_get_method_nesting_depth_report` with a module name that has analyzed classes but zero methods
- **THEN** the system returns 3 empty `Distribution` objects

### Requirement: Nesting depth evaluation task

The system SHALL include an analysis task that evaluates the method nesting depth distribution per module and produces architecture findings with recommendations, cross-referenced with cyclomatic complexity data.

#### Scenario: Deep nesting detected
- **WHEN** a module has methods with nesting depth greater than 4
- **THEN** the evaluation SHOULD flag these as readability refactoring candidates

#### Scenario: High CC but low nesting
- **WHEN** a module has methods with cyclomatic complexity greater than 10 but nesting depth less than 3
- **THEN** the evaluation SHOULD note these as "flat complex" - high complexity but readable structure

#### Scenario: Low CC but high nesting
- **WHEN** a module has methods with cyclomatic complexity less than 5 but nesting depth greater than 5
- **THEN** the evaluation SHOULD flag these as "nested spaghetti" - low complexity but deeply nested, poor readability
