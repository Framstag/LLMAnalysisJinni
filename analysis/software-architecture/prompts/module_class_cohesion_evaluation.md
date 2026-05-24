## Current Goal

* The project may have one or more build modules.
* Evaluate the class cohesion of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_get_class_cohesion_report' tool with the module name as parameter to get field count and method-to-field ratio distributions.
* Additionally, call the 'java_get_field_visibility_report' tool for field count context.
* Evaluate the result in relation to common architecture guidelines regarding class cohesion.
* This should include:
  * The distribution of field counts per class — classes with many fields (more than 10) combined with many methods (more than 20) may be god classes.
  * The field-to-method ratio — a high ratio (more fields than methods) may indicate data classes.
  * A low ratio (many methods with few fields) may indicate utility classes or procedural design.
  * Pay attention to differences between production, test, and generated code.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}
