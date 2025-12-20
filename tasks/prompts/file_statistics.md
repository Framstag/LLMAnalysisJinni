## Current Goal

* The project may have one or more build modules.
* We want some basic file-based statistics for each build module.
* If there are multiple modules, we want to skip the root module, if it does not have any code.
* We analyse each module separately buy repeatedly calling this prompt for each module.

## Facts

The following build systems have been identified:

{{#build.buildsystems~}}
* Build System: '{{name}}', variant: '{{variant}}'
{{/build.buildsystems}}

{{> facts/build_system_directories.md}}

The following modules have been identified:

{{# modules.modules~}}
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

{{> facts/programming_language_wildcards.md}}

## Solution strategy

* Use the 'GetStatisticsForMatchingFilesInDirRecursively' tool, to gather some basic statistics for each build module.

## Hints

* Use only wildcards for programming languages that are stated above in the facts.
