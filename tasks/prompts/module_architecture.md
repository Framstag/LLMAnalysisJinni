## Current Goal

* The project may have one or more build modules.
* We want a description of the principle architecture of each build module.
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

[# th:if="${state.modules.modules[loopIndex].purpose != null && state.modules.modules[loopIndex].purpose.purpose != null}"]
The purpose of this build module is: "[(${state.modules.modules[loopIndex].purpose.purpose})]"
[/]

The following programming languages have been identified for this module:

[# th:each="language : ${state.modules.modules[loopIndex].programmingLanguages.programmingLanguages}" th:remove="tag"]
* Programming language "[(${language.name})]"
[/]

Here is a table that maps each programming languages to a wildcard expression:

| Programming language | Wildcard Expression |
|----------------------|---------------------|
| Java                 | *.java              |
| Kotlin               | *.kt                |
| Javascript           | *.js                |
| Typescript           | *.ts                |
| C++                  | *.(h|hpp|cpp|cc)    |
| Gradle Kotlin DSL    | *.kts               |

The following table shows wildcards matching specific files for the given build system:

| Build System | Variant    | Wildcard Expression |
|--------------|------------|---------------------|
| Maven        |            | "pom.xml"           |
| Gradle       | Classic    | "*.gradle"          |
| Gradle       | Kotlin DSL | "*gradle.kts"       |

Depending on the build system the following source directories can be expected:

|Build System |Variant     | Source-Directories |
|-------------|------------|--------------------|
|Maven        |            | src                |
|Gradle       | Classic    | src                |
|Gradle       | Kotlin DSL | src                |

## Solution strategy

* If there are multiple modules and the current module path is the root module, assume that it does not have sources and respond without gathering these information.
* If this is the root module and it is the only module of the project, scan the build module directory root for files typical for a programming language.
* If it is not the root module, scan the build module directory root for files typical for the used programming language.
* Use the "GetMatchingFilesInDirRecursively" Tool to scan the module directory.
* Call it either for the source directory, if defined by the build system, or the module directory, if not.
* Analyse the architecture of the module based on the file names and their structure.
* Return the architecture description of the module.
* You must not scan individual files yet.
* You thus must not use other tools!

## Hints

* You must only return clean JSON content without any prefix or postfix!

