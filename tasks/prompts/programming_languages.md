## Goal

* The project may have one or more build modules.
* We want to identify all used programming languages for each build module.
* If there are multiple modules, we want to skip the root module, if it does not have any code.
* We analyse each module separately buy repeatedly calling this prompt for each module.

## Facts

The following modules have been identified:

[# th:each="module : ${state.modules.modules}" th:remove="tag"]
* "[(${module.name})]" in "[(${module.path})]"
  [/]

The current module to analyse is named: "[(${state.modules.modules[loopIndex].name})]"
The path of this build module is: "[(${state.modules.modules[loopIndex].path})]"

The following build systems have been identified:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
* [(${buildsystem.name})]
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

## Solution strategy

* If there are multiple modules and the current module path is "", assume that it does not have sources and just return immediately.  
* Scan the build module directory root for files typical for a programming language.
* Sum up all found programming languages.

## Hints

* You must call the "FileCountPerFileTypeAndDirectory" tool for the build module path.
* From the result you should deduct which programming languages are used in this module.
* Do not try to read individual files at this point!
* Do not use any other tool!
* It may be possible that a possible does not hold any source code but it only a build submodule that creates other artefacts.
