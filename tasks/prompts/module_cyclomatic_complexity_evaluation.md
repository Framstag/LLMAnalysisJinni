## Current Goal

* The project may have one or more build modules.
* Evaluate the cyclomatic complexity of each module separately.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

The following build modules have been identified:

{{#modules.modules~}}
* Module "{{name}}" in directory "{{path}}"
{{/modules.modules}}

Regarding the now to be analyzed module:

{{#with (lookup modules.modules loopIndex)}}
The current module to analyze is named: "{{name}}"
The path of this build module is: "{{path}}"
The current module is a root module: {{root}}

The following programming languages have been identified for this module:

{{#programmingLanguages.programmingLanguages~}}
* Programming language "{{name}}"
{{/programmingLanguages.programmingLanguages}}
{{/with}}

## Solution strategy

* If one of the programming languages is 'Java', call
  the 'GetCyclomaticComplexityModuleReport' tool.
* Evaluate the result in relation to common architecture guidelines.
* This should include:
  * The value range regarding the cyclomatic complexity.
  * The distribution of the cyclomatic complexity values.
