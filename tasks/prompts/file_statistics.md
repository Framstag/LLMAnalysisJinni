## Current Goal

* The project may have one or more build modules.
* We want some basic file-based statistics for each build module.
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

Regarding the now to be analyzed module:

The current module to analyse is named: "[(${state.modules.modules[loopIndex].name})]"
The path of this build module is: "[(${state.modules.modules[loopIndex].path})]"
The current module is a root module: [(${state.modules.modules[loopIndex].root})]

The following programming languages have been identified for this module:

[# th:each="language : ${state.modules.modules[loopIndex].programmingLanguages.programmingLanguages}" th:remove="tag"]
* Programming language "[(${language.name})]"
[/]

[# th:insert="~{facts/programming_language_wildcards}" /]

## Solution strategy

* Use the 'GetStatisticsForMatchingFilesInDirRecursively' tool, to gather some basic statistics for each build module.
* 
## Hints

* Use only wildcards for programming languages that are stated above in the facts.
