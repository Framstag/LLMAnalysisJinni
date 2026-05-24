# Class Inheritance Analysis

## Purpose

Provides tools and evaluation tasks for analyzing class inheritance depth and interface implementation patterns across production, test, and generated code in a module. Enables detection of deep hierarchies, interface pollution, and missing abstraction layers.

## Requirements

### Requirement: Module inheritance distribution report

The system SHALL provide a tool that returns inheritance metrics per module, including inheritance depth distribution (per class) and interface count distribution (per class).

The report MUST include separate distributions for production code, test code, and generated code. Inheritance depth is computed by traversing the superclass chain; depth 0 means no explicit superclass (extends Object only). Interface count is the number of interfaces a class directly implements.

#### Scenario: Successful inheritance report generation
- **WHEN** the LLM calls `java_get_inheritance_report` with a module name
- **THEN** the system returns 3 pairs of `Distribution` objects - one pair (depth + interface count) each for production, test, and generated code

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_inheritance_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Class with multiple interfaces
- **WHEN** a class in the module implements 3 or more interfaces
- **THEN** the interface count distribution reflects count 3 for that class

#### Scenario: Inheritance depth resolved across module classes
- **WHEN** class A extends B, and B extends C (all in same module)
- **THEN** A has depth 2, B has depth 1, C has depth 0

### Requirement: Inheritance evaluation task

The system SHALL include an analysis task that evaluates inheritance patterns per module and produces architecture findings with recommendations.

#### Scenario: Deep hierarchy detected
- **WHEN** a module has classes with inheritance depth greater than 3
- **THEN** the evaluation SHOULD flag these as potentially fragile deep hierarchies

#### Scenario: Interface pollution detected
- **WHEN** a class implements more than 5 interfaces
- **THEN** the evaluation SHOULD flag this as a possible Single Responsibility Principle violation

#### Scenario: No inheritance at all
- **WHEN** a module's production code has zero inheritance depth greater than 0 across all classes
- **THEN** the evaluation SHOULD note the absence of inheritance-based polymorphism
