# Documentation Ratio Analysis

## Purpose

Provides tools and evaluation tasks for analyzing the ratio of documented vs undocumented classes and methods across production, test, and generated code in a module. A class or method is considered documented if its documentation string is non-null and non-empty.

## Requirements

### Requirement: Documentation ratio report

The system SHALL provide a tool that returns the ratio of documented vs undocumented classes and methods in a given module.

A class or method is considered documented if its `getDocumentation()` returns a non-null, non-empty string. The report MUST include separate statistics for production code, test code, and generated code, covering both class-level and method-level documentation.

#### Scenario: Successful documentation ratio report generation
- **WHEN** the LLM calls `java_get_documentation_ratio_report` with a module name
- **THEN** the system returns 3 `Distribution` objects (prod/test/gen) showing documented class count, total class count, documented method count, and total method count

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_documentation_ratio_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Documentation ratio evaluation task

The system SHALL include an analysis task that evaluates documentation coverage per module.

#### Scenario: Low documentation coverage detected
- **WHEN** a module has less than 50 percent of production classes documented
- **THEN** the evaluation SHOULD flag this as low documentation coverage
