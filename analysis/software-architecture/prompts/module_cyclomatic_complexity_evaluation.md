## Current Goal

* The project may have one or more build modules.
* Evaluate the cyclomatic complexity of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{> macros/list_of_modules.md}}

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}

{{#if programmingLanguages.programmingLanguages.length}}
## Solution strategy

* If one of the programming languages is 'Java', call
  the 'GetCyclomaticComplexityModuleReport' tool.
* Evaluate the result in relation to common architecture guidelines.
* This should include:
  * The value range regarding the cyclomatic complexity.
  * The distribution of the cyclomatic complexity values.
{{~else~}}
## Solution Strategy

* Since no programming languages were found, just return an empty array of findings.
{{/if}}
{{/with}}

