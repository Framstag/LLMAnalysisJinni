## Current Goal

* The project may have one or more build modules.
* We want to collect raw analysis data for further analysis o the module architecture.
* We analyse each module separately by repeatedly calling this prompt for each module.

## Facts

The following build modules have been identified:

{{#modules.modules~}}
* Module "{{name}}" in directory "{{path}}"
  {{/modules.modules}}

Regarding the now to be analyzed module:

{{#with (lookup modules.modules loopIndex)}}
The current module to analyse is named: "{{name}}"
The path of this build module is: "{{path}}"
The current module is a root module: {{root}}

The following programming languages have been identified for this module:

{{#programmingLanguages.programmingLanguages~}}
* Programming language "{{name}}"
  {{/programmingLanguages.programmingLanguages}}
  {{/with}}

## Solution strategy

* If one of the programming languages is 'Java', call
  the 'JavaGenerateModuleAnalysisReport'.

## Hints
