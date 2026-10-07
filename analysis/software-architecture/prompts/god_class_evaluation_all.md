## Current Goal

* Evaluate the god class candidates of every Java module in one batch task.
* Call **`java_get_all_god_class_rankings`** once.
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

* Call `java_get_all_god_class_rankings` once.
* The tool returns, per module, the production classes ranked by how far each stands out from its peers, best candidate first.
* Every factor is a **percentile within the module**, never an absolute threshold and never a pass or fail verdict. The result says that a class stands out among its peers, not that it is bad.
* Read the factors, not only the score. `WMC` is the sum of method cyclomatic complexity. `cohesion (TCC-like)` is the share of method pairs that both work on the state of the class, and it is an approximation, not TCC. `foreign data accesses` counts accesses to fields declared outside the class. `maximum nesting depth`, `maximum method lines` and `module coupling` are the remaining factors.
* Size alone is not the finding. A large class whose methods all work on its own state has a high cohesion percentile and is correctly not flagged as a god class; say so when you see it.
* The `dominantFactor` names what drove a class's rank, so a reader can disagree with the weighting. Use it: a low cohesion percentile is the usual driver of a real god class candidate.
* `excludedClassCount` states how many classes of the module were not ranked, because they are test code, generated code or outside the module's namespace. Mention it when it is large.
* Recommend a concrete direction: extract a class, split responsibilities, move the foreign data access behind a method.

## Response Requirements

* Use the `ModuleBatchEvaluation` response schema.
* Each `moduleEvaluations[]` entry must include `moduleName`, `reasoning`, and `evaluations`.
* If a finding applies to multiple modules, include the full affected module list in the finding text, for example: `Affected modules: core, api, web.`
* Do not use vague grouped wording such as `several modules`, `many modules`, or `some modules`.
* Do not apply an initial per-module finding limit.
* Each `evaluations[]` item MUST be a JSON object with fields: `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`. Do NOT use plain strings.
