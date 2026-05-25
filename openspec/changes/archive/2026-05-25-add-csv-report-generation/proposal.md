## Why

All Java analysis `@Tool` methods return distribution objects (e.g., `List<Distribution>`) that the LLM reads and evaluates — but the raw data these distributions are built from is invisible to humans reading the final report. When the LLM flags "class X has high coupling", the reader cannot see which imports caused it, or the full sorted list of coupled classes. Following the `DependenciesAndLicenses.csv` pattern from `SBOMTool.java`, each analysis tool should also write a CSV report file with the raw, sorted data so findings are transparent and actionable.

## What Changes

- All Java `@Tool` methods in `JavaTool.java` that return analysis distributions will additionally write CSV report files to the working directory
- Each CSV contains the raw sorted data behind the distribution (e.g., visibility: list of classes per visibility level; coupling: list of classes with their Ce score)
- Some tools may produce multiple CSV files with different views (e.g., raw data + statistical summary)
- Pattern: `CsvWriter` from `de.siegmar.fastcsv.writer`, resolve path from `context.getWorkingDirectory()`, filename derived from tool name

### Tools to enhance

| Tool | CSV content |
|------|-------------|
| `getVisibilityDistributionReport` | Class name, visibility, isStatic, isFinal — sorted by visibility |
| `getInheritanceReport` | Class name, inheritance depth, interface count — sorted by depth |
| `getMethodComplexityReport` | Class, method, param count, LoC — sorted by param count desc |
| `getCircularDependencyReport` | Cycle number, class names in cycle — per cycle |
| `getCouplingReport` | Class name, efferent coupling, module dependency — sorted by coupling desc |
| `getTestCoverageReport` | Class name, has test (yes/no) — untested first |
| `getFieldVisibilityReport` | Class name, field name, visibility, static, final |
| `getClassCohesionReport` | Class name, field count, method count, ratio |
| `getMethodCountReport` | Class name, method count — sorted by count desc |
| `getDocumentationRatioReport` | Class name, has class doc, method count, documented methods |
| `getDataClassReport` | Class name — list of data class candidates |
| `getBooleanParameterReport` | Class name, method name, param count |
| `getMethodNestingDepthReport` | Class name, method name, nesting depth |
| `getCyclomaticComplexityModuleReport` | Class name, method name, CC value |

## Capabilities

### New Capabilities

- `csv-report-generation`: All Java analysis @Tool methods write CSV report files with raw sorted data to the working directory alongside returning Distribution objects to the LLM.

### Modified Capabilities

- *(None — new capability)*

## Impact

- **Tools**: `JavaTool.java` — add `context` field injection, add CSV writing logic to all analysis report methods
- **Output**: Each analysis run produces N new CSV files in the working directory (one per tool method called)
- **Dependencies**: Uses existing `de.siegmar.fastcsv` library (already in project for `SBOMTool.java`)
- **No breaking changes**: Distribution/List return values unchanged — CSV is additive