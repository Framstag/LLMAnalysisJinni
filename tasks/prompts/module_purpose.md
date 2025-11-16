## Current Goal

* The project may have one or more build modules.
* We want a short description of the purpose of each build module.
* We analyse each module separately by repeatedly calling this prompt for each module.

## Facts

From the README we have extracted the following general project description:

"[(${state.project.goal})]"

The following build systems have been identified:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
* Build System: '[(${buildsystem.name})]', variant: '[(${buildsystem.variant})]'
[/]

The following build modules have been identified:

[# th:each="module : ${state.modules.modules}" th:remove="tag"]
* Module "[(${module.name})]" in directory "[(${module.path})]"
[/]

Regarding the now to be analyzed module:

* The current module to analyse is named: "[(${state.modules.modules[loopIndex].name})]"
* The path of this build module is: "[(${state.modules.modules[loopIndex].path})]"
* The current module is a root module: [(${state.modules.modules[loopIndex].root})]

The following programming languages have been identified for this module:

[# th:each="language : ${state.modules.modules[loopIndex].programmingLanguages.programmingLanguages}" th:remove="tag"]
* Programming language "[(${language.name})]"
[/]

[# th:insert="~{facts/programming_language_wildcards}" /]

[# th:insert="~{facts/build_system_wildcards}" /]

[# th:insert="~{facts/build_system_directories}" /]

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
