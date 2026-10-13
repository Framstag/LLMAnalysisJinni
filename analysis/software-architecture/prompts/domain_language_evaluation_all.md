## Current Goal

* Evaluate the language of every Java module in one batch task: does it name its own domain, does one concept keep one name, do the names follow the code structure, and does the module speak about what it says it is for.
* Call **`java_get_all_domain_vocabulary_reports`** once.
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

The modules describe themselves as follows:

{{#modules.modules}}
* Module {{name}}: {{purpose.purpose}}
{{/modules.modules}}

The glossary that fixes the meaning of a term for the whole project:

{{#domainGlossary.terms}}
* Term "{{term}}" is {{classification}} and is established by: {{#each modules}}{{this}} {{/each}}
{{/domainGlossary.terms}}

{{#if domainGlossary.ambiguousTerms}}
Terms that different modules use with different meanings:

{{#domainGlossary.ambiguousTerms}}
* Term "{{term}}": {{#each meanings}}"{{meaning}}" in {{#each modules}}{{this}} {{/each}}; {{/each}}
{{/domainGlossary.ambiguousTerms}}
{{/if}}

## Solution Strategy

* Call `java_get_all_domain_vocabulary_reports`.
* Use the counted shares, the leading terms, the tier totals, the coverage values and the naming cohesion of each module as returned. The reported values are the evidence; do not estimate a value of your own.
* A module that is reported with `sufficientEvidence` false carries no metrics, because its vocabulary is too small to judge. Do not invent findings for it.
* A module in the `skipped` part of the tool result is not evaluated at all; ignore it.
* Judge four aspects per module and group findings that apply to several modules:
  1. **Domain or plumbing**: how far the module's vocabulary names its own domain. Judge the technical share against the purpose of the module: a module whose purpose is a mechanism, a binding or a transport may be technical, a module whose purpose is a domain concept may not.
  2. **Naming consistency**: whether one concept keeps one name inside the module. Look at the leading terms for two names for the same thing, and at a single name that is used for two different things. Name the variants you found.
  3. **Naming cohesion**: whether the names follow the code structure. Use the reported naming cohesion, which is the mean name overlap of the types that reference each other minus the mean name overlap of the types that do not. Name the value you read and read it as the sign of the difference, not as an absolute quality.
  4. **Purpose alignment**: whether the module speaks about what it says it is for. Use the reported coverage values and the reported lists of purpose terms the code does not use and of leading code terms the description does not mention.
* Report a leading term of a module that the glossary establishes in another module as a foreign term, and judge it:
  * A term that a boundary type, a transported value or a shared concept explains is expected. Say so and recommend nothing.
  * A term of another module's domain that neither the module purpose nor a boundary role explains is a leak. Name the term, the module that establishes it and the module that uses it.
* A term that the glossary records as ambiguous is not a leak by itself: the boundary carries no translation, which is what the ambiguity shows.
* Recommend a rename only while naming the term, the module and the concept the term should express.
* Do not recommend a general improvement of naming.
* Every grouped finding must explicitly list all affected modules, for example: `Affected modules: core, api, web.`
* Do not use vague grouped wording such as `several modules`, `many modules`, or `some modules`.
* Do not apply an initial per-module finding limit, but do report a module only where the evidence gives you something to say: a module whose vocabulary is unremarkable, whose names fit its references and whose description matches its code needs no finding, and a table of empty findings helps nobody.

## Hints

* The full evidence of a module, one row per term with its tier and its channel counts, is written as CSV beside the other reports. Use the returned values, and name the term as the tool reported it.
* A module whose language is entirely technical is not a defect when the module is plumbing. Say which of the two it is and why.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `ModuleBatchEvaluation` response schema.
* Each `moduleEvaluations[]` entry must include `moduleName`, `reasoning`, and `evaluations`.
* If a finding applies to multiple modules, include the full affected module list in the finding text.
* Each `evaluations[]` item MUST be a JSON object with fields: `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`. Do NOT use plain strings.
