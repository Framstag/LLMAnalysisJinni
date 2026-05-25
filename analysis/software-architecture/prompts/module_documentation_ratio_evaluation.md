## Current Goal
* The project may have one or more build modules.
* Evaluate the documentation coverage of each module separately.
## Facts
{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}
{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy
* If 'Java', call 'java_get_documentation_ratio_report' with the module name.
* Evaluate: less than 50% documented classes indicates poor documentation coverage.
* Include both class-level and method-level documentation ratios.
{{~else~}}## Solution Strategy
* Since no programming languages, return empty findings.
{{/if}}{{/with}}
