## Goal

* The project may have one or more build modules.
* We want to identify all used programming languages for each build module.
* If there are multiple modules, we want to skip the root module if it does not have any code.
* We analyze each module separately by repeatedly calling this prompt for each module.

## Facts

{{> macros/list_of_build_systems.md}}
{{> facts/build_system_directories.md}}
{{> macros/list_of_modules.md}}

{{#with (lookup modules.modules loopIndex)}}
{{> macros/current_loop_module.md}}
{{/with}}

{{> facts/programming_language_wildcards.md}}

## Solution strategy

* If there are multiple modules and the current module path is the root module, assume that it may not have sources. In this case explicitly only scan directories you expect from common usage of the build system and do not recursively scan the whole source directory, since this will result in scanning the complete project (and all other modules).   
* If it is not the root module, scan the build module directory root for files typical for a programming language.
* Sum up all found programming languages.

## Hints

* If you decide to look for programming language usage, you must call the "FileCountPerFileTypeAndDirectory" tool for the source directory (if defined by the build system) or the build module path. In that case you should deduct which programming languages are used in this module.
* It may be possible that modules do not hold any source code, but it is only a build submodule that creates other artifacts or uses programming languages unknown to us.
* Do not try to read individual files at this point!
* Do not use any other tool!
* You must only return clean JSON content without any prefix or postfix!
* If a version cannot be determined, leave the version in the response empty.