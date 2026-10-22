# Spec Delta

## MODIFIED Requirements

### Requirement: Annotation distribution report

The system SHALL provide a tool that returns the distribution of annotation types (by qualified name) used across classes and methods in a given module.

The report MUST include separate distributions for production code, test code, and generated code. Each distribution maps annotation qualified names to their total count (class-level + method-level combined).

#### Scenario: Successful annotation report generation

- **WHEN** the LLM calls `java_get_annotation_report` with a module name
- **THEN** the system returns 3 `Distribution` objects — one each for production, test, and generated code — mapping annotation qualified names to their total occurrence counts

#### Scenario: Module not yet analyzed

- **WHEN** the LLM calls `java_get_annotation_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Module with no annotations

- **WHEN** the LLM calls `java_get_annotation_report` with a module name that has analyzed classes but zero annotations
- **THEN** the system returns 3 empty `Distribution` objects

#### Scenario: Built-in annotations are counted

- **WHEN** a class or a method of the module carries a Java built-in annotation
- **THEN** the distributions SHALL count that annotation like every other annotation type

#### Scenario: Annotations outside the parsed nodes are not counted

- **WHEN** an annotation type appears only in the import graph of the module
- **THEN** the distributions SHALL NOT contain that annotation type
