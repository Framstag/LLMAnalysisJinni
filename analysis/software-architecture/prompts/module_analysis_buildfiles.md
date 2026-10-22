## Current goal

* Load and Analyze the quality of the build files of this module.

## Facts

{{> macros/list_of_modules.md}}
{{> macros/list_of_build_systems.md}}

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}
{{/with}}

{{> facts/build_system_wildcards.md}}

## Solution Strategy

* The build file of the current module is the file of the build system of the project, at the path of the current module. This is the build file to read and to analyse.
* Read that build file.
* Analyze the quality of the build files you read.

## Hints

* Do only analyze the files belonging to the detected build systems. Do not analyze further files loaded from the build system but which are not part of the build system but belong to quality tools or similar that are triggered by the build system.
* If the current module is the root module, its build file is at the root of the project.
