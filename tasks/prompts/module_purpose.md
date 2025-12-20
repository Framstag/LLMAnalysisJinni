## Current Goal

* The project may have one or more build modules.
* We want a short description of the purpose of each build module.
* We analyse each module separately by repeatedly calling this prompt for each module.

## Facts

From the README we have extracted the following general project description:

"{{project.goal}}"

The following build systems have been identified:

{{#build.buildsystems~}}
* Build System: '{{name}}', variant: '{{variant}}'
{{/build.buildsystems}}

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

{{> facts/programming_language_wildcards.md}}

{{> facts/build_system_wildcards.md}}

{{> facts/build_system_directories.md}}

## Solution strategy

* If there are multiple modules and the current module path is the root module, assume that it does not have sources and do not look for programming languages and respond without gathering these information.
* If this is the root module and it is the only module of the project, scan the build module directory root for files typical for a programming language.
* If it is not the root module, scan the build module directory root for files typical for a programming language.
* Use the "GetMatchingFilesInDirRecursively" Tool to scan the module directory. 
* Call it either for the source directory, if defined by the build system, or for module directory, if not.
* Analyse the purpose of the module based on the file names and their structure.
* Return the purpose of the module.

## Hints

* You must only return clean JSON content without any prefix or postfix!
