## Context

CSV report files (`NestingDepth.csv`, `CyclomaticComplexity.csv`, etc.) are written to the workspace working directory with static filenames. All 22 JavaTool report methods + 2 SBOMTool report methods use `context.getWorkingDirectory().resolve("<static-name>.csv")`. Since the pipeline iterates over modules via `loopOn: /modules/modules` and all iterations share the same `workingDirectory`, each module's report overwrites the previous one.

## Goals / Non-Goals

**Goals:**
- Each report type gets its own subdirectory (e.g., `NestingDepth/`)
- Report file placed inside that subdirectory with filename = module name + extension
- Report type directory auto-created if missing
- All callers updated to use new path scheme

**Non-Goals:**
- Changing report data format, CSV headers, or content
- Changing JSON module reports (`storeModuleReport`)
- Changing LLM interaction, prompts, schemas, or task YAML
- Changing how the pipeline loops over modules

## Decisions

**Decision 1: Path transformation rule**

Path `workingDir/<reportType>.csv` → `workingDir/<reportType>/<moduleName>.csv`

Example: `workingDir/NestingDepth.csv` for module `MyModule` → `workingDir/NestingDepth/MyModule.csv`

Rationale: Each module gets its own file within a report-type directory. Solves the overwrite problem.

**Decision 2: Directory creation in CsvReportWriter, not callers**

Add `FILES.createDirectories(workingDir.resolve(subdirName))` inside each `CsvReportWriter` method before resolving the full file path.

Rationale: Centralizes the directory-creation concern. Callers don't need to know about the directory layout — they just change which path they pass.

**Decision 3: Module name sanitization**

Module names may contain spaces or special chars. Sanitize by replacing non-alphanumeric chars (except `-`, `.`, `_`) with `_` — same pattern used by `moduleNameToReportName()` in JavaTool.

Rationale: Ensures valid filenames across platforms while keeping names readable.


**Decision 4: Module name as filename within report directory**

Resolved: Module name differentiates files within each report-type directory.
Structure: `workingDir/<reportType>/<sanitizedModuleName>.<ext>`

Rationale: User explicitly confirmed this approach. Solves the overwrite problem definitively.

## Risks / Trade-offs

- [SBOM reports] `SBOMTool.writeLicencesReports()` not per-module. Use report name as fallback filename: `AllLicenses/AllLicenses.csv`, `DependenciesAndLicenses/DependenciesAndLicenses.csv`.

1. **SBOM filenames**: Use report name as filename body when no module context (e.g., `AllLicenses/AllLicenses.csv`)?
