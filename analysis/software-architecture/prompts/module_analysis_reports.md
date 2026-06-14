## Current Goal

* The project may have one or more build modules.
* We want to collect raw analysis data for further analysis of the module architecture.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}

{{> macros/programming_languages_for_module.md}}

{{> macros/subdirectories_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call the 'java_generate_module_analysis_report' tool with the module name.
* The tool returns the raw report id. Convert it to one report descriptor:
  * `programmingLanguage`: `Java`
  * `reportName`: returned report id
  * `reasoning`: short note that raw Java report data was generated for this module
* If there is no matching analysis tool for a detected programming language, skip this language.
* If no programming language was found to analyze, return an empty `reports` array.

## Hints
{{~else~}}
## Solution strategy

* Since no programming languages have been found, no analysis is possible. Thus, return an empty `reports` array.
{{/if}}
{{/with}}
