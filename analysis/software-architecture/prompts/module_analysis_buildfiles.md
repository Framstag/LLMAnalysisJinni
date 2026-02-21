## Current goal

* Load and Analyze the quality of the build files of this module.

## Facts

{{> macros/list_of_modules.md}}
{{> macros/list_of_build_systems.md}}

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{> macros/programming_languages_for_module.md}}
{{/with}}

## Solution Strategy

* Locate the build files for the current module based on the knowledge regarding the used build systems.
* Read the located build files.
* Analyze the quality of the build files.

## Hints

* Do only analyze the files belonging to the detected build systems. Do not analyze further files loaded from the build system but which are not part of the build system but belong to quality tools or similar that are triggered by the build system.
* Make sure that files you try to load actually exist by checking the directory before or by using the 'DoesFileExist' tool.