# Spec Delta

## ADDED Requirements

### Requirement: Only types that declare a class of the module become classes of the module

The system SHALL admit a type to the module model only when its file declares a class of the module.

`javac` writes such a class file for the two compilation unit level types of the language: `package-info` and
`module-info`. Neither SHALL become a build unit, a node of the reference graph, a class of the raw module
report or a metric input, and such a file SHALL be reported as one that contributed no type of the module.

#### Scenario: A compilation unit level type declares no class of the module
- **WHEN** a class file declares the compilation unit level type `package-info` or `module-info`
- **THEN** it declares no class, no field and no method of the module, only the annotations of its package or
  the description of its module

#### Scenario: Package annotation descriptor is not a class of the module
- **WHEN** a module tree holds a `package-info.class` beside the classes of its package
- **THEN** the module report holds no build unit whose name ends with `package-info`
- **AND** the class count of the module does not include the descriptor
- **AND** the descriptor is reported as a file that contributed no type

#### Scenario: Module descriptor is not a class of the module
- **WHEN** a module tree holds a `module-info.class`
- **THEN** the module report holds no build unit for it
- **AND** no package entry is created for the descriptor

#### Scenario: The classes beside the descriptor are unaffected
- **WHEN** a `package-info.class` is dropped
- **THEN** every class of the same package is still parsed, and its references are still recorded

#### Scenario: Phantom class does not reach any analysis
- **WHEN** the class reference graph, the split candidate analysis, the god class ranking or the class
  dependency graph runs on such a module
- **THEN** no node of the graph is a compilation unit level type
- **AND** no metric distribution of the module counts one

## MODIFIED Requirements

### Requirement: Raw module report carries the weighted reference record

The system SHALL persist the per-class weighted reference record and the per-method access counters in the raw module report.

The system SHALL retain the flat list of referenced type names beside that record, so the existing metric tools keep their existing inputs and outputs for reports written before the record existed and for reports that carry it.

The raw module report SHALL record the format version of the model that wrote it, and the system SHALL NOT reuse a report that records an older version.

#### Scenario: Existing metric tools unchanged
- **WHEN** any existing Java metric tool that consumes the flat list of referenced type names runs against a report in the new format
- **THEN** its inputs and results are identical to the previous format

#### Scenario: Report written before the weighted record stays readable
- **WHEN** an existing Java metric tool runs against a report that carries no weighted reference record
- **THEN** it returns the same result as before this change, and does not report an absence of references as an absence of dependencies

#### Scenario: Weighted record is available after a round trip
- **WHEN** a raw module report is written and then loaded by a later tool
- **THEN** API width, traffic and the structural classification of each reference are still available

#### Scenario: Report written before the compilation unit level types were excluded is regenerated
- **WHEN** the report task reuses an existing raw module report that records a format version older than the current one
- **THEN** the module is parsed again and the report is replaced
- **AND** the result states the recorded version, the current version and that the report was regenerated

#### Scenario: Outdated report is refused instead of analysed
- **WHEN** the diagram, split candidate, god class or class dependency analysis is called for a module whose report records an older format version
- **THEN** the analysis stores no result for it and states that the report has to be regenerated
