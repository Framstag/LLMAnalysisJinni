# Circular Dependency Analysis

## Purpose

Provides tools and evaluation tasks for detecting circular dependencies between classes within a build module using Tarjan's SCC algorithm on the import-based dependency graph. Enables identification of architecture cycles that make code fragile, hard to test, and resistant to modularization.

## Requirements

### Requirement: Circular dependency report

The system SHALL provide a tool that detects circular dependencies between classes within a build module using Tarjan's SCC algorithm on the import-based dependency graph.

The report MUST list each detected cycle with the classes involved and the cycle length.

#### Scenario: Successful cycle detection
- **WHEN** the LLM calls `java_get_circular_dependency_report` with a module name
- **THEN** the system returns a list of detected cycles, each containing the classes involved in the cycle and the cycle length

#### Scenario: No cycles found
- **WHEN** the LLM calls `java_get_circular_dependency_report` with a module name that has no circular dependencies
- **THEN** the system returns an empty list

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_circular_dependency_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Circular dependency evaluation task

The system SHALL include an analysis task that evaluates circular dependency reports per module and produces architecture findings with recommendations.

#### Scenario: Cycle detected
- **WHEN** a module has one or more circular dependencies
- **THEN** the evaluation SHOULD flag each cycle and recommend breaking the dependency by extracting shared interfaces or restructuring responsibilities

#### Scenario: Large cycle detected
- **WHEN** a module has a cycle involving more than 3 classes
- **THEN** the evaluation SHOULD flag this as a particularly problematic architecture smell requiring refactoring
