# entry-point-detection Specification

## Purpose
Counts the entry points of a project, the classes a caller can reach a module through, so that the places where a
module can be replaced from the outside can be found and judged.

## Requirements

### Requirement: Entry point candidates are counted

The system SHALL provide a tool that returns, for every class of every Java module of the project, the module the
class belongs to, whether the class is production, test or generated code, whether the class declares a main method,
the names of the annotations the class carries and the interfaces the class implements.

#### Scenario: Class with a main method is reported

- **WHEN** the tool is called for a project whose module `app` holds a class with a static main method
- **THEN** the result names the class, the module `app`, the code category of the class and that it declares a main
  method

#### Scenario: Annotations and interfaces are reported with the class

- **WHEN** the tool is called for a project whose module `web` holds a class that carries an annotation and
  implements an interface
- **THEN** the result names the annotation and the interface beside the class, so that a reader can judge what kind
  of entry point the class is without opening the source

#### Scenario: Module without a report is absent

- **WHEN** the tool is called for a project in which one module has no Java report
- **THEN** the result holds no row for that module and the tool result names it as skipped

### Requirement: A main method is recognised as an entry point

The system SHALL report a class as carrying a main method when the class declares a method named `main` that is
static and returns nothing, and SHALL NOT report a method with that name alone as one.

#### Scenario: Instance method named main is not a main method

- **WHEN** a class declares a method named `main` that is not static
- **THEN** the class is not reported as carrying a main method

#### Scenario: Static method named main with a return type is not a main method

- **WHEN** a class declares a static method named `main` that returns a value
- **THEN** the class is not reported as carrying a main method

### Requirement: The entry point evidence is written as CSV

The system SHALL write the entry point evidence as a CSV report at project level, one row per class that carries a
main method, an annotation or an implemented interface, with the fields that make the row readable on its own.

#### Scenario: Entry point CSV is written

- **WHEN** the entry point tool has counted the classes of the project
- **THEN** a CSV file holds one row per class with the class name, the module, the code category, the annotations and
  the implemented interfaces

### Requirement: The entry point order groups the modules

The system SHALL return the entry point candidates ordered by module and by class name within a module, so that two
runs of the same project return the same order.

#### Scenario: Two runs return the same order

- **WHEN** the entry point tool is called twice for the same project
- **THEN** both calls return the candidates in the same order
