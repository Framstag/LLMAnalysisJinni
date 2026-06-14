## Current Goal

* Evaluate method nesting depth across all Java modules in one batch task.
* Call **`java_get_all_method_nesting_depth_reports`** once.
* Produce findings for all modules in one response using `moduleEvaluations[]`.

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

* Call `java_get_all_method_nesting_depth_reports`.
* Use the returned compact distributions for each module.
* Evaluate all modules together. Group findings common to multiple modules once.
* Every grouped finding must explicitly list all affected modules, for example: `Affected modules: core, api, web.`
* Do not use vague grouped wording such as `several modules`, `many modules`, or `some modules`.
* Do not apply an initial per-module finding limit.

## Response Requirements

* Use the `ModuleBatchEvaluation` response schema.
* `moduleEvaluations` must contain one entry for every module with Java nesting-depth data.
* Each `moduleEvaluations[]` entry must include `moduleName`, `reasoning`, and `evaluations`.
* Do not collapse module-specific findings only into the top-level `reasoning` field.
* Each `evaluations` array must contain at least one finding. If there is no actionable finding, include one `NONE`/`NONE` finding such as "No notable nesting-depth issue".
* If a finding applies to multiple modules, include the full affected module list in the finding text.
