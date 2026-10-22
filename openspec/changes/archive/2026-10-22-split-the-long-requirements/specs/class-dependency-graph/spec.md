# Spec Delta

## MODIFIED Requirements

### Requirement: Raw module report carries the weighted reference record

The system SHALL persist the per-class weighted reference record and the per-method access counters in the raw module report.

The system SHALL retain the flat list of referenced type names beside that record, so the existing metric tools keep their existing inputs and outputs.

The raw module report SHALL record the format version of the model that wrote it.

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
