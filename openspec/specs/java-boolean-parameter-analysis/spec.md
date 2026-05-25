# Boolean Parameter Analysis

## Purpose

Provides tools and evaluation tasks for identifying methods with excessive boolean parameters across production, test, and generated code in a module. A method is flagged if its descriptor contains 3 or more boolean type codes in the parameter portion.

## Requirements

### Requirement: Boolean parameter abuse report

The system SHALL provide a tool that identifies methods with excessive boolean parameters in a given module. A method is flagged if its descriptor contains 3 or more `Z` (boolean type code) entries in the parameter portion.

The report MUST include separate lists for production code, test code, and generated code.

#### Scenario: Successful boolean parameter report generation
- **WHEN** the LLM calls `java_get_boolean_parameter_report` with a module name
- **THEN** the system returns a list of method names flagged for having 3+ boolean parameters

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_boolean_parameter_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Boolean parameter evaluation task

The system SHALL include an analysis task that evaluates boolean parameter abuse per module.

#### Scenario: Flag argument smell detected
- **WHEN** a module has methods with 3 or more boolean parameters
- **THEN** the evaluation SHOULD flag these as flag argument smells and suggest splitting into separate methods
