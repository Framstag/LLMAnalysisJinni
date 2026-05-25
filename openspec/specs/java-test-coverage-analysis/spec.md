# Test Coverage Analysis

## Purpose

Provides tools and evaluation tasks for analyzing test coverage per module by matching production classes to test classes via naming conventions. Matches using both `{ClassName}Test` and `{ClassName}Tests` suffixes to cover JUnit 4/5 and Spring conventions. Enables detection of untested production classes, low test-to-production ratios, and modules lacking test code entirely.

## Requirements

### Requirement: Test coverage report

The system SHALL provide a tool that returns test coverage statistics per module by matching production classes to test classes via naming convention.

The report MUST include separate statistics for each module: total production classes, classes with a matching test class, classes without a matching test class, and test-to-production ratio. Matching is done by checking if a test class with the name `{ClassName}Test` or `{ClassName}Tests` exists in any test BuildUnit of the same module.

#### Scenario: Successful test coverage report generation
- **WHEN** the LLM calls `java_get_test_coverage_report` with a module name
- **THEN** the system returns 2 `Distribution` objects: test coverage summary (total/tested/untested/ratio) and list of untested production classes

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_test_coverage_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Test coverage evaluation task

The system SHALL include an analysis task that evaluates test coverage per module and produces architecture findings with recommendations.

#### Scenario: Untested production class detected
- **WHEN** a module has production classes without matching test classes
- **THEN** the evaluation SHOULD flag the untested classes, especially core domain classes

#### Scenario: Low test coverage ratio
- **WHEN** a module has a test-to-production class ratio below 0.5 (less than 50 percent of classes have tests)
- **THEN** the evaluation SHOULD flag this as low test coverage

#### Scenario: No test code at all
- **WHEN** a module has production code but no test BuildUnits
- **THEN** the evaluation SHOULD flag this as having no tests
