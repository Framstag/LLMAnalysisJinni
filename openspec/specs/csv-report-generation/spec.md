# CSV Report Generation

## Purpose

All Java analysis @Tool methods write CSV report files with raw sorted data to the working directory alongside returning Distribution objects to the LLM. CSV reports make findings transparent and actionable by providing class-level raw data behind aggregated distributions. The utilities are centralized in `CsvReportWriter` in the `tools.common` package.

## Requirements

### Requirement: CSV reports for all Java analysis tools

Every Java analysis @Tool method that returns analysis distributions or findings SHALL also write a CSV report file with raw sorted data to the working directory.

#### Scenario: Tool called with data
- **WHEN** an analysis @Tool method is called and data exists
- **THEN** a CSV file is written to the working directory with a name derived from the tool name

#### Scenario: Tool called with no data
- **WHEN** an analysis @Tool method is called and returns an empty list
- **THEN** no CSV file is written (or a header-only CSV is written)

### Requirement: CSV format

CSV files SHALL contain the raw sorted data behind the analysis distributions, including separate sections for production, test, and generated code where applicable.

#### Scenario: CSV is readable
- **WHEN** a CSV report file is opened
- **THEN** it contains a header row and data rows sorted by the most relevant metric column

### Requirement: CSV latency is acceptable

CSV writing SHALL NOT add significant latency to tool execution.

#### Scenario: Large module
- **WHEN** a tool processes a module with many classes
- **THEN** CSV writing completes in negligible time compared to the LLM call
