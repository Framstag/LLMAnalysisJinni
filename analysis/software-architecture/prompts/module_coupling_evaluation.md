## Current Goal

* The project may have one or more build modules.
* Evaluate the coupling of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_coupling_report' tool with the module name as parameter.
* Evaluate the result in relation to common architecture guidelines regarding coupling.
* This should include:
  * The distribution of efferent coupling (Ce) values across classes.
  * Classes with coupling greater than 20 are potential hub classes — they depend on too many external types and are fragile.
  * Modules with mean coupling greater than 10 across production classes may be too tightly coupled.
  * The module dependency distribution shows which external modules are most depended upon.
  * If more than 50% of imports target a single external module, this may indicate framework lock-in.
  * Pay attention to differences between production, test, and generated code.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
