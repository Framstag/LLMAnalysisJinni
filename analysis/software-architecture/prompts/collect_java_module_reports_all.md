## Current Goal

* Collect raw Java analysis reports for all detected Java build modules in one batch task.
* Do not call the per-module `java_generate_module_analysis_report` tool for each module.
* Prefer existing `java/Java_<moduleName>.json` report files. Generate only missing reports.

## Facts

{{#with modules.modules}}
The project has {{length}} build modules:

| Module | Path | Root |
|--------|------|------|
{{#each this}}
| {{name}} | `{{path}}` | {{root}} |
{{/each}}
{{/with}}

## Solution Strategy

* Call **`java_generate_all_module_analysis_reports`** once.
* The tool reads the current analysis state, selects modules whose detected programming language is `Java`, reuses existing raw report files from the `java/` subdirectory, and generates missing reports.
* Return the tool result directly as the response.
* Include `reports` for generated or reused Java reports and `skipped` for non-Java or error modules.

## Response Requirements

* Use the `ModuleAnalysisReportsAll` response schema.
* `reports` entries must include `moduleName`, `status`, `programmingLanguage`, `reportName`, and `reasoning`.
* `skipped` entries must include `moduleName`, `status`, and `reasoning`.
* Do not invent report names. Use the report names returned by the tool.
