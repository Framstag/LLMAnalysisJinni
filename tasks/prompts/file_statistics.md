## Current Goal

* The project may have one or more build modules.
* We want some basic file-based statistics for each build module.
* If there are multiple modules, we want to skip the root module, if it does not have any code.
* We analyse each module separately buy repeatedly calling this prompt for each module.

## Facts

The following build systems have been identified:

{{> macros/list_of_build_systems.md}}
{{> facts/build_system_directories.md}}
{{> macros/list_of_modules.md}}

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}
{{/with}}

{{> facts/programming_language_wildcards.md}}

## Solution strategy

* Use the 'GetStatisticsForMatchingFilesInDirRecursively' tool to gather some basic statistics for each build module.

## Hints

* Use only wildcards for programming languages that are stated above in the facts.
