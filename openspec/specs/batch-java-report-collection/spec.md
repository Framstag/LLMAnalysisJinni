# batch-java-report-collection Specification

## Purpose
TBD - created by archiving change batch-java-module-evaluation. Update Purpose after archive.
## Requirements
### Requirement: All-module Java raw report collection task
The system SHALL provide a non-loop analysis task named `CollectJavaModuleReportsAll` that generates or reuses Java raw module analysis reports for all Java build modules in one task invocation.

#### Scenario: Batch report collection task is active by default
- **WHEN** the software-architecture task pipeline is initialized
- **THEN** `CollectJavaModuleReportsAll` is active by default and has no `loopOn` property

#### Scenario: Batch report collection depends on module discovery data
- **WHEN** `CollectJavaModuleReportsAll` is scheduled
- **THEN** it depends on module discovery, programming language detection, and module subdirectory classification before execution

### Requirement: Java-side all-module report iteration
The system SHALL implement Java-side iteration over detected modules for raw Java report collection instead of asking the LLM to call a per-module report tool repeatedly.

#### Scenario: Java tool iterates all detected modules
- **WHEN** `java_generate_all_module_analysis_reports` is called
- **THEN** the Java tool reads `analysisState.modules.modules` and iterates all detected modules

#### Scenario: LLM is not responsible for per-module report calls
- **WHEN** the batch report collection prompt is rendered
- **THEN** it asks the LLM to call `java_generate_all_module_analysis_reports` once, not `java_generate_module_analysis_report` for each module

### Requirement: Java module selection
The system SHALL select modules for Java raw report collection based on detected programming languages in analysis state.

#### Scenario: Java module selected
- **WHEN** a module has `programmingLanguages.programmingLanguages` containing `Java`
- **THEN** the module is included in the batch report collection result

#### Scenario: Non-Java module skipped
- **WHEN** a module has no detected Java programming language
- **THEN** the module is listed in skipped results with a reason and no Java raw report is generated for it

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

### Requirement: Raw Java module report descriptors
The system SHALL return a structured list of generated or reused Java raw module report descriptors.

#### Scenario: Successful report descriptor
- **WHEN** a Java module report is generated or reused
- **THEN** the descriptor contains `moduleName`, `programmingLanguage`, `reportName`, and `reasoning`

#### Scenario: Skipped module descriptor
- **WHEN** a module is skipped
- **THEN** the skipped descriptor contains `moduleName` and `reasoning`

### Requirement: Raw Java module reports remain source of truth
The system SHALL keep existing `Java_<moduleName>.json` files as the source of truth for Java metric report tools.

#### Scenario: Metric tools read raw module reports
- **WHEN** a Java metric batch tool computes a metric
- **THEN** it loads the existing raw module report instead of reparsing Java source files

### Requirement: Batch report collection idempotency
The system SHALL make batch Java report collection idempotent enough for retry after partial failure.

#### Scenario: Retry after partial generation
- **WHEN** batch report collection generated reports for some modules and failed for others
- **THEN** a retry reuses already-generated report files and attempts only missing reports

#### Scenario: One bad module does not invalidate descriptors
- **WHEN** one Java module cannot be processed
- **THEN** the task returns skipped or error descriptors for that module when possible and continues processing other modules

