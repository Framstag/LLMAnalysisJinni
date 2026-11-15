# sbom-dependency-read Specification

## Purpose
Defines how the dependencies of a loaded SBOM are read: how the root of the dependency graph is resolved when the
SBOM does not hold an entry for it, which route the read took, which components are the project itself rather than a
dependency, and what a read that yields nothing says about the reason.

## Requirements

### Requirement: The dependencies are read from the graph of the SBOM

The system SHALL read the dependencies of the application from the dependency graph of the loaded SBOM, and SHALL
report a dependency with the reference, the name built from its group and its name, and its version.

#### Scenario: A graph that holds the root component

- **WHEN** the dependency graph of the SBOM holds an entry whose reference is the reference of the root component
- **THEN** the dependencies are the components the entry depends on

#### Scenario: A dependency is named by its group and its name

- **WHEN** a component of the graph carries a group and a name
- **THEN** the dependency is reported as group and name joined by a colon, and with its version

### Requirement: The root of the graph is resolved, not required

The system SHALL resolve the root of the dependency graph by trying its routes in order, and SHALL NOT report an empty
dependency list because the graph holds no entry for the root component.

#### Scenario: An aggregator build without a root entry

- **WHEN** the graph holds no entry for the reference of the root component and it holds an entry for each project
  module of the aggregator
- **THEN** the dependencies are the components the project module entries depend on
- **AND** the result states that the read resolved the root through the project modules

#### Scenario: A graph that holds neither

- **WHEN** the graph holds no entry for the root component and no entry for a project module
- **THEN** the read reports what it could not resolve and which route it tried, and it reports no dependency

#### Scenario: The reference of the root carries a project path

- **WHEN** the reference of the root component names the root project path of an aggregator build
- **THEN** the read recognises that reference as the root of that build

### Requirement: A read that yields nothing names the reason

The system SHALL state the reason in the result of a dependency read that yields no dependency, and SHALL NOT return
an empty list without a reason.

#### Scenario: An SBOM that holds no dependency graph

- **WHEN** the loaded SBOM holds no dependency graph
- **THEN** the result states that the graph is missing, and it names the SBOM as the file it was read from

#### Scenario: A graph whose root could not be resolved

- **WHEN** neither route of the root resolution finds an entry
- **THEN** the result names the reference it looked for and the routes it tried

#### Scenario: No SBOM loaded

- **WHEN** the tool is called before an SBOM was loaded
- **THEN** the result states that no SBOM is loaded, instead of an empty list that reads as a property of the project

### Requirement: The components of the project are not dependencies

The system SHALL NOT report a component that is a module of the analysed project as a dependency of that project.

#### Scenario: A project module in the graph

- **WHEN** a component of the graph is one of the project modules the aggregator build declares
- **THEN** it is not part of the dependencies of the application
- **AND** the result states how many such components it left out

### Requirement: The read states the route it took

The system SHALL name in its result which route resolved the root of the graph, so that a reader can tell a complete
dependency list from a list that rests on a fallback.

#### Scenario: The direct route

- **WHEN** the root component holds an entry of its own
- **THEN** the result names the direct route

#### Scenario: The fallback route

- **WHEN** the read resolved the root through the project modules
- **THEN** the result names that route, so that a reader does not read the list as the one the root component declares
