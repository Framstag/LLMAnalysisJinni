# Spec Delta

## ADDED Requirements

### Requirement: The class record carries the interface flag

The system SHALL record in the class record of the raw Java module report whether the type is an interface, so that
a consumer can tell an interface from a class without guessing from the shape of the class, and SHALL raise the
format version of the report so that a report written before the record carried the flag is regenerated.

#### Scenario: The class record holds the interface flag

- **WHEN** a raw Java module report is written
- **THEN** the record of a class holds whether the type is an interface

#### Scenario: An interface with a default method is an interface

- **WHEN** a type declares a default method or a constant and is an interface
- **THEN** the record of that type holds that it is an interface, and a consumer of the report does not read it as
  a class

#### Scenario: A report without the interface flag is regenerated

- **WHEN** the report task reuses an existing raw Java module report whose format version is older than the current
  one
- **THEN** the module is parsed again and the report is replaced, and the result states the recorded version, the
  current version and that the report was regenerated

#### Scenario: The existing metric tools read the report unchanged

- **WHEN** a Java metric tool reads a report of the new format
- **THEN** its inputs and its result are the same as before the interface flag was added to the class record
