## Current Goal

* Evaluate the class reference structure inside every Java module and propose where a module could be separated.
* Call **`java_generate_all_dependency_diagrams`**, **`java_get_all_class_dependency_graphs`** and **`java_get_all_split_candidates`** once each.
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

* Call the three tools once each, in the order listed above.
* `java_get_all_class_dependency_graphs` reports, per module, how many classes and reference edges its graph holds, how much was excluded, the distribution of edge weights and the heaviest couplings.
* `java_get_all_split_candidates` reports, per module, a ladder of candidate separations ordered by **ascending cost**, so the cheapest separation comes first.
* The **cost** of a separation is the weight of the tie that had to be severed, which is the strongest tie between the two groups that came apart. One strong tie means two sides are not independent, however few ties there are, so a heavy tie appears at its own weight and never before the cheaper steps.
* A separation may cut across package boundaries. Each group names the package contributing most of its members and how many members do not belong to it.
* The ladder is derived from the production classes of the module only. Test classes reference everything and would dominate the structure.
* A structural relation - a superclass, an implemented interface, a declared field type - carries no reference site and therefore no separation cost; it is listed, not priced.
* The `minimumCut` figure is the least total coupling that separates a module. It can differ from the first ladder step, because it may prefer one heavy tie over several light ties. When it is `NOT_COMPUTED` for a module, say so and do not invent a number.
* Every proposed split must state its price: the bottleneck cost and the total severed weight of the separation that produces it.
* When no cheap separation exists for a module, state that instead of proposing one.
* Do not treat a low number of ties as independence; a single heavy tie is what decides it.

## Response Requirements

* Use the `ModuleBatchEvaluation` response schema.
* Each `moduleEvaluations[]` entry must include `moduleName`, `reasoning`, and `evaluations`.
* If a finding applies to multiple modules, include the full affected module list in the finding text, for example: `Affected modules: core, api, web.`
* Do not use vague grouped wording such as `several modules`, `many modules`, or `some modules`.
* Each `evaluations[]` item MUST be a JSON object with fields: `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`. Do NOT use plain strings.
* The diagrams are generated into the analysis state by the tool. Do not restate or rewrite their source in your response.
