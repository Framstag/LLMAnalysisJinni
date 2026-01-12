## Current Goal

* The project may have one or more build modules.
* We want to collect raw analysis data for further analysis of the module architecture.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{> macros/list_of_modules.md}}

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}

{{> macros/programming_languages_for_module.md}}

{{> macros/subdirectories_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'JavaGenerateModuleAnalysisReport' tool.
* If there is no matching analysis tool for a detected programming language, just skip this language and continue with the next in the list.
* If no programming language was found to analyse, just return an empty array of reports.

## Hints
{{~else~}}
## Solution strategy

* Since no programming languages have been found, no analysis is possible. Thus, return an empty array of reports.
{{/if}}
{{/with}}