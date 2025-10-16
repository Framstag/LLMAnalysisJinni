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

[# th:each="module : ${state.modules.modules}" th:remove="tag"]
* Module "[(${module.name})]" in directory "[(${module.path})]"
[/]

Regarding the now to be analyzed module

The current module to analyse is named: "[(${state.modules.modules[loopIndex].name})]"
The path of this build module is: "[(${state.modules.modules[loopIndex].path})]"
The current module is a root module: [(${state.modules.modules[loopIndex].root})]

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

|Build System |Variant     |Wildcard Expression |
|-------------|------------|--------------------|
|Maven        |            | "pom.xml"          |
|Gradle       | Classic    | "*.grade"          |
|Gradle       | Kotlin DSL | "*gradle.kts"      |

Depending on the build system the following source directories can be expected:

|Build System |Variant     | Source-Directories |
|-------------|------------|--------------------|
|Maven        |            | src                |
|Gradle       | Classic    | src                |
|Gradle       | Kotlin DSL | src                |

## Solution strategy

* If there are multiple modules and the current module path is the root module, assume that it does not have sources and do not look for programming languages and respond without gathering these information.
* If it is not the root module, scan the build module directory root for files typical for a programming language.
* Use the "GetFilesOverview" Tool (possibly repeatedly) to scan each module directory. Either for the source directory, if defined by the build system, or for module directory, if not.
* Analyse the purpose of each module based on the file names and their structure.
* Return the purpose of the module

## Hints

* You can iteratively use the "GetFilesOverview" tool using wildcards specific for a programming language.
* Use a call for each module root.
* Make use you use a high value for "depth" to make sure you will see the code files.
* You must only return clean JSON content without any prefix or postfix!
