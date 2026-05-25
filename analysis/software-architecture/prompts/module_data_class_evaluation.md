## Current Goal
* The project may have one or more build modules.
* Evaluate data class candidates of each module separately.
## Facts
{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}
{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy
* If 'Java', call 'java_get_data_class_report' with the module name.
* Evaluate: data classes with all fields private + only getters/setters, or all fields public, indicate anemic domain models.
{{~else~}}## Solution Strategy
* Since no programming languages, return empty findings.
{{/if}}{{/with}}
