# Spec Delta

## ADDED Requirements

### Requirement: The class record carries the source file of the class

The system SHALL record the source file a class was declared in in the class record of the raw Java module report, so
that the history of the project can be joined to a class, and SHALL raise the format version of the report so that a
report written before the record carried the source file is regenerated.

#### Scenario: The class record holds the source file

- **WHEN** a raw Java module report is written
- **THEN** the record of a class holds the path of the file the class was declared in

#### Scenario: A report without the source file is regenerated

- **WHEN** the report task reuses an existing raw Java module report whose format version is older than the current
  one
- **THEN** the module is parsed again and the report is replaced, and the result states the recorded version, the
  current version and that the report was regenerated

#### Scenario: The existing metric tools read the report unchanged

- **WHEN** a Java metric tool reads a report of the new format
- **THEN** its inputs and its result are the same as before the source file was added to the class record
