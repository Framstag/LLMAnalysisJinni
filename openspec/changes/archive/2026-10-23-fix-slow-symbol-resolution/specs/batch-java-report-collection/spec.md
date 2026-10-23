# Spec Delta

## ADDED Requirements

### Requirement: The collection of a large module completes

The system SHALL complete the collection of the raw report of a module whose source tree is large, and the symbol
resolution of the collection SHALL NOT parse a source tree of the module with the comment attribution of the module
parse, because resolving a symbol does not need a comment.

#### Scenario: A module with a large source tree is collected

- **WHEN** the raw report of a module of several hundred source files is collected
- **THEN** the collection completes and writes the report of the module

#### Scenario: The documentation counts are unchanged

- **WHEN** the report of a module is collected after the symbol resolution stopped parsing with comment attribution
- **THEN** the documentation of its classes and methods, and every metric that counts it, is the same as before

#### Scenario: The report of the module is otherwise unchanged

- **WHEN** the report of a module collected after this change is compared with the report of the same module collected
  before it
- **THEN** the classes, the methods, the references, the annotations and the counted weights are the same

### Requirement: A module whose collection fails is skipped, not stalling

The system SHALL report a module whose collection does not complete as a skipped module with the reason, and SHALL
continue the collection of the other modules of the project, so that one module cannot stall the collection of all.

#### Scenario: A module that cannot be collected

- **WHEN** the collection of one module does not complete
- **THEN** that module is named as skipped with its reason and the other modules are collected
