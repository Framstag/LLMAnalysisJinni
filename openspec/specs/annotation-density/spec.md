# Annotation Density Analysis

## Purpose

Provides tools and evaluation tasks for analyzing annotation type distribution and @Override ratio across production, test, and generated code in a module. Enables detection of framework lock-in via annotation concentration, missing @Override discipline, and unusually high annotation density per class.

## Requirements

### Requirement: Annotation distribution report

The system SHALL provide a tool that returns the distribution of annotation types (by qualified name) used across classes and methods in a given module.

The report MUST include separate distributions for production code, test code, and generated code. Each distribution maps annotation qualified names to their total count (class-level + method-level combined). Java built-in annotations (e.g., `@Override`, `@Deprecated`, `@SuppressWarnings`) are included. Annotation types that appear only in the import graph but not on the parsed class/method nodes are excluded.

#### Scenario: Successful annotation report generation
- **WHEN** the LLM calls `java_get_annotation_report` with a module name
- **THEN** the system returns 3 `Distribution` objects — one each for production, test, and generated code — mapping annotation qualified names to their total occurrence counts

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_annotation_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Module with no annotations
- **WHEN** the LLM calls `java_get_annotation_report` with a module name that has analyzed classes but zero annotations
- **THEN** the system returns 3 empty `Distribution` objects

### Requirement: Annotation @Override ratio report

The system SHALL provide, as part of the annotation report, the ratio of methods annotated with `@Override` to total methods in the module, split by production, test, and generated code.

#### Scenario: Override ratio included in report
- **WHEN** the LLM calls `java_get_annotation_report` with a module name
- **THEN** the returned distributions include entries for `java.lang.Override` with the count of methods bearing this annotation

### Requirement: Annotation evaluation task

The system SHALL include an analysis task that evaluates annotation usage patterns per module and produces architecture findings with recommendations.

#### Scenario: High annotation density detected
- **WHEN** a module has an unusually high number of annotations per class compared to the project average
- **THEN** the evaluation SHOULD flag this as potential annotation overuse or framework coupling

#### Scenario: Low @Override ratio detected
- **WHEN** a module has fewer than 20% of its methods annotated with `@Override` in classes that extend a superclass or implement interfaces
- **THEN** the evaluation SHOULD flag this as a potential missing @Override concern

#### Scenario: Framework-specific annotation concentration
- **WHEN** more than 60% of annotations in a module belong to a single framework (e.g., `org.springframework.*`)
- **THEN** the evaluation SHOULD note this as potential framework lock-in