# seam-detection Specification

## Purpose
Counts the seams of a project, the interfaces with the classes that implement them and the modules that consume
them, so that a port that could be replaced can be told apart from an interface nobody uses.

## Requirements

### Requirement: Seam evidence is counted

The system SHALL provide a tool that returns, for every interface of every Java module of the project, the module
that declares the interface, the classes that implement it, the modules those classes belong to, the classes of the
declaring module that reference the interface, and the modules of the project that import the interface.

#### Scenario: Interface with implementors and consumers is reported

- **WHEN** the tool is called for a project whose module `core` declares an interface that two classes of `core`
  implement and whose module `web` imports
- **THEN** the result names the interface, the declaring module `core`, both implementing classes, the module `core`
  as the module of the implementors, and `web` as an importing module

#### Scenario: Interface without implementor is reported as unimplemented

- **WHEN** the tool is called for a project that holds an interface no class implements
- **THEN** the result still names the interface and reports it with an empty implementor list, instead of leaving
  it out

#### Scenario: Module without a report is absent

- **WHEN** the tool is called for a project in which one module has no Java report
- **THEN** the result holds no row for that module and the tool result names it as skipped

### Requirement: A port is told apart from an unused interface

The system SHALL report for every interface whether it is implemented more than once and whether it is consumed from
more than one module, and SHALL name the interfaces that satisfy both as port candidates, so that a replaceable
seam can be told apart from an interface nobody uses.

#### Scenario: Interface implemented once and used nowhere is not a port candidate

- **WHEN** an interface of the project has a single implementor and no consumer outside its own module
- **THEN** the result reports it as not a port candidate and states that it is implemented once

#### Scenario: Interface implemented several times and used across modules is a port candidate

- **WHEN** an interface of the project has more than one implementor and is imported by more than one module
- **THEN** the result names it as a port candidate and reports both counts beside it

### Requirement: The seam evidence is written as CSV

The system SHALL write the seam evidence as a CSV report at project level, one row per interface, with the fields
that make the row readable on its own.

#### Scenario: Seam CSV is written

- **WHEN** the seam tool has counted the interfaces of the project
- **THEN** a CSV file holds one row per interface with the interface name, the declaring module, the implementor
  count, the consumer count and the importing modules

### Requirement: The seam order is strongest first

The system SHALL return the interfaces ordered so that the interfaces with more implementors and more consumers come
first, and SHALL NOT classify an interface as good or bad.

#### Scenario: Port candidates come first

- **WHEN** the project holds an interface with several implementors and several importing modules and an interface
  with none
- **THEN** the interface with several implementors and importing modules is returned before the one with none
