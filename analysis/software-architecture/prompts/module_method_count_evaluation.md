## Current Goal
* The project may have one or more build modules.
* Evaluate the method count per class of each module separately.
## Facts
{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}
{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy
* If 'Java', call 'java_get_method_count_report' with the module name.
* Evaluate: classes with more than 20 methods may be god class candidates.
* Include distribution range and highlight extreme values.
{{~else~}}## Solution Strategy
* Since no programming languages, return empty findings.
{{/if}}{{/with}}
