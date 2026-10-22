# Spec Delta

## MODIFIED Requirements

### Requirement: Module method nesting depth distribution report

The system SHALL provide a tool that returns the distribution of method nesting depth (max scoping depth of control structures) across all classes in a given module.

The report MUST include separate distributions for production code, test code, and generated code.

#### Scenario: Successful nesting depth report generation

- **WHEN** the LLM calls `java_get_method_nesting_depth_report` with a module name
- **THEN** the system returns 3 `Distribution` objects - one each for production, test, and generated code - mapping nesting depth integers to method counts

#### Scenario: Module not yet analyzed

- **WHEN** the LLM calls `java_get_method_nesting_depth_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Module with no code

- **WHEN** the LLM calls `java_get_method_nesting_depth_report` with a module name that has analyzed classes but zero methods
- **THEN** the system returns 3 empty `Distribution` objects

## ADDED Requirements

### Requirement: The nesting depth of a method is measured by its nested control structures

The nesting depth of a method SHALL be the maximum level of nested `if`, `for`, `while`, `do-while`, `switch`, `try` and for-each blocks in its body.

A control structure whose body is a single statement without braces SHALL NOT add a level of depth. A depth of 0 SHALL mean that the method holds no control structure at all.

A method that was parsed from a class file SHALL have a nesting depth of 0, because the bytecode carries no scoping structure.

#### Scenario: Nested control structures increase the depth

- **WHEN** a method holds an `if` inside a `for`
- **THEN** the nesting depth of that method SHALL be 2

#### Scenario: A statement without braces does not add a level of depth

- **WHEN** a control structure holds a single statement without braces
- **THEN** the nesting depth SHALL NOT increase because of that statement

#### Scenario: A control structure at the top level has a depth of 1

- **WHEN** a method holds a control structure that is not nested in another one
- **THEN** the nesting depth of that method SHALL be 1

#### Scenario: A method parsed from a class file has a depth of 0

- **WHEN** a method was parsed from a class file only
- **THEN** its nesting depth SHALL be 0
