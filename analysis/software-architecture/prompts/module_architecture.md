## Current Goal

* The project may have one or more build modules.
* We want a description of the principle architecture of each build module.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

From the README we have extracted the following general project description:

"{{project.goal}}"

{{> macros/list_of_build_systems.md}}
{{> macros/list_of_modules.md}}

{{#with (lookup modules.modules loopIndex)}}

{{> macros/current_loop_module.md}}

{{#if (and purpose purpose.purpose)}}
The purpose of this build module is: "{{purpose}}"
{{/if}}

{{> macros/programming_languages_for_module.md}}
{{/with}}

{{> facts/programming_language_wildcards.md}}

{{> facts/build_system_wildcards.md}}

{{> facts/build_system_directories.md}}

## Solution strategy

* If there are multiple modules and the current module path is the root module, assume that it does not have sources and respond without gathering this information.
* If this is the root module, and it is the only module of the project, scan the build module directory root for files typical for a programming language.
* If it is not the root module, scan the build module directory root for files typical for the used programming language.
* Use the "GetMatchingFilesInDirRecursively" Tool to scan the module directory.
* Call it either for the source directory, if defined by the build system, or the module directory, if not.
* Analyse the architecture of the module based on the file names and their structure.
* Return the architecture description of the module.
* You must not scan individual files yet.
* You thus must not use other tools!

## Hints

* You must only return clean JSON content without any prefix or postfix!

