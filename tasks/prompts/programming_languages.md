## Goal

* The project may have one or more build modules.
* We want to identify all used programming languages for each build module.
* If there are multiple modules, we want to skip the root module, if it does not have any code.
* We analyse each module separately buy repeatedly calling this prompt for each module.

## Facts

The following build systems have been identified:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
* Build System: '[(${buildsystem.name})]', variant: '[(${buildsystem.variant})]'
[/]


Depending on the build system the following source directories can be expected:

|Build System |Variant     | Source-Directories |
|-------------|------------|--------------------|
|Maven        |            | src                |
|Gradle       | Classic    | src                |
|Gradle       | Kotlin DSL | src                |

The following modules have been identified:

[# th:each="module : ${state.modules.modules}" th:remove="tag"]
* Module "[(${module.name})]" in directory "[(${module.path})]"
[/]

Regarding the now to be analyzed module

The current module to analyse is named: "[(${state.modules.modules[loopIndex].name})]"
The path of this build module is: "[(${state.modules.modules[loopIndex].path})]"
The current module is a root module: [(${state.modules.modules[loopIndex].root})]

[# th:insert="~{facts/programming_language_wildcards}" /]

## Solution strategy

* If there are multiple modules and the current module path is the root module, assume that it does not have sources and do not look for programming languages and respond without gathering these information.  
* If it is not the root module, scan the build module directory root for files typical for a programming language.
* Sum up all found programming languages.

## Hints

* If you decided to look for programming language usage, you must call the "FileCountPerFileTypeAndDirectory" tool for the source directory (if defined by the build system) or the build module path. In that case you should deduct which programming languages are used in this module.
* It may be possible, that a modules does not hold any source code but it only a build submodule that creates other artefacts.
* Do not try to read individual files at this point!
* Do not use any other tool!
* You must only return clean JSON content without any prefix or postfix!
* You must not use the "GetAllFilesInDirRecursively" for the root directory, since this will not perform for larger projects.
