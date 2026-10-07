# Spec Delta

## MODIFIED Requirements

### Requirement: Reuse existing Java raw report files
The system SHALL reuse existing raw Java module report files by default when they are already present and were produced by the current report format.

#### Scenario: Existing raw report reused
- **WHEN** `workingDirectory/Java_<moduleName>.json` exists and records the current report format version
- **THEN** the batch report collection returns a descriptor for the existing report and does not re-parse the module source files

#### Scenario: Missing raw report generated
- **WHEN** `workingDirectory/Java_<moduleName>.json` is missing for a Java module
- **THEN** the batch report collection generates the raw report file and returns a descriptor for the generated report

#### Scenario: Outdated raw report regenerated
- **WHEN** `workingDirectory/Java_<moduleName>.json` exists but records an older report format version
- **THEN** the batch report collection re-parses the module and overwrites the report with one in the current format

#### Scenario: Regeneration is not silent
- **WHEN** a raw report is regenerated because its format version was outdated
- **THEN** the returned descriptor for that module states the reason, so a stale report is never mistaken for a current one

## ADDED Requirements

### Requirement: Raw module report records its format version

The system SHALL record the report format version inside each raw Java module report file.

The system SHALL expose the format version of a report in the result of any tool that loads it, and SHALL report a format mismatch rather than treating a report as usable.

#### Scenario: Version is present in the written report
- **WHEN** a raw Java module report is generated
- **THEN** the file contains the format version of the model it was produced with

#### Scenario: Version is available to consumers
- **WHEN** a Java metric tool loads a raw module report
- **THEN** the report's format version is available to the tool without parsing the model

#### Scenario: Version mismatch is reported
- **WHEN** a tool requires data that a report in an older format does not carry
- **THEN** the tool reports the mismatch and names the regeneration step, instead of returning an empty or partial result
