## Current Goal

* Evaluate field visibility distributions across all Java modules in one batch task.
* Call **`java_get_all_field_visibility_reports`** once.
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

* Call `java_get_all_field_visibility_reports`.
* Use the returned compact distributions for each module.
* Evaluate all modules together. Group findings common to multiple modules once.
* Every grouped finding must explicitly list all affected modules, for example: `Affected modules: core, api, web.`
* Do not use vague grouped wording such as `several modules`, `many modules`, or `some modules`.
* Do not apply an initial per-module finding limit.

## Response Requirements

* Use the `ModuleBatchEvaluation` response schema.
* Each `moduleEvaluations[]` entry must include `moduleName`, `reasoning`, and `evaluations`.
* If a finding applies to multiple modules, include the full affected module list in the finding text.
* Each `evaluations[]` item MUST be a JSON object with fields: `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`. Do NOT use plain strings.
