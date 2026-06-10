## ADDED Requirements

### Requirement: Report type subdirectory with module-name filename

`CsvReportWriter` SHALL organize CSV report files into subdirectories named after the report type. Each module's report SHALL use the module name as the filename body.

Given a report type name (e.g., `NestingDepth`) and module name (e.g., `core-lib`), the output path SHALL be `workingDir/NestingDepth/core-lib.csv`.

Module names SHALL be sanitized: non-alphanumeric characters (except `-`, `.`, `_`) replaced with `_`.

#### Scenario: File written to report-type subdirectory with module name

- **WHEN** `CsvReportWriter.writeCsv(workingDir, "NestingDepth/core-lib.csv", header, rows)` is called
- **THEN** the file at `workingDir/NestingDepth/core-lib.csv` SHALL contain the CSV data

#### Scenario: Directory auto-created when missing

- **WHEN** `CsvReportWriter.writeMapCsv(workingDir, "FieldVisibility/my-module.csv", data)` is called and `workingDir/FieldVisibility/` does not exist
- **THEN** `workingDir/FieldVisibility/` SHALL be created before writing the file

#### Scenario: Module name with spaces sanitized

- **WHEN** a module named `My Module` generates a `NestingDepth` report
- **THEN** the output path SHALL be `workingDir/NestingDepth/My_Module.csv` (space replaced with `_`)

#### Scenario: Directory creation failure produces error

- **WHEN** the report type directory cannot be created (e.g., permission denied)
- **THEN** an error SHALL be logged to stderr and the method SHALL return without writing the file

### Requirement: Consistent naming across all callers

All 22 CSV report calls in `JavaTool` SHALL construct the filename as `<reportType>/<sanitizedModuleName>.csv`. The 2 SBOM calls in `SBOMTool` SHALL use `<reportType>/<reportType>.csv` since they have no module context.

#### Scenario: JavaTool reports use module-name filenames

- **WHEN** `JavaTool.getCyclomaticComplexityModuleReport("core-lib")` is called
- **THEN** the output CSV SHALL be written to `workingDir/CyclomaticComplexity/core-lib.csv`

#### Scenario: SBOM reports use report-name fallback filename

- **WHEN** `SBOMTool.writeLicencesReports()` writes `AllLicenses.csv`
- **THEN** the file SHALL be written to `workingDir/AllLicenses/AllLicenses.csv`

#### Scenario: Module name passed to all report methods

- **WHEN** `JavaTool.getVisibilityDistributionReport(moduleName)` is called with any moduleName
- **THEN** the filename passed to `CsvReportWriter` SHALL include that moduleName

### Requirement: Minimum call-site changes

The `CsvReportWriter` method signatures SHALL remain unchanged. Callers SHALL pass the full relative path `<reportType>/<filename>` as the filename parameter. `CsvReportWriter` SHALL extract the parent directory from the path for `createDirectories`.

#### Scenario: Caller constructs path with module name

- **WHEN** a caller invokes `CsvReportWriter.writeMultiIntMapCsv(workingDir, "NestingDepth/" + moduleName + ".csv", ...)`
- **THEN** `CsvReportWriter` SHALL resolve the parent directory from the path, create it, and write the file

#### Scenario: Report method uses moduleName parameter

- **WHEN** `JavaTool.getInheritanceReport(moduleName)` constructs the filename
- **THEN** it SHALL use the `moduleName` parameter to build the relative path `"InheritanceDepth/" + moduleName + ".csv"` (sanitized)