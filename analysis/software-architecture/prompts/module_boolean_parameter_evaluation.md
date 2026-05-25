## Current Goal
* The project may have one or more build modules.
* Evaluate boolean parameter abuse of each module separately.
## Facts
{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}
{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy
* If 'Java', call 'java_get_boolean_parameter_report' with the module name.
* Evaluate: methods with 3+ boolean parameters are flag argument smells and should be split into separate methods.
{{~else~}}## Solution Strategy
* Since no programming languages, return empty findings.
{{/if}}{{/with}}
