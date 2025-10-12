## Goal

Identify all used programming languages

## Solution strategy

* Scan the repository for files typical for a programming language.
* Sum up all found programming languages.

## Hints

* You must call the "FileCountPerFileTypeAndDirectory" tool for each build module root separately.
* From the result you should deduct which programming languages are used in each module.
* Do not try to read individual files at this point!
* Do not use any other tool!

## Facts

The following build systems have been identified:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
* [(${buildsystem.name})]
[/]

The following build module roots have been identified:

[# th:each="module : ${state.modules.modules}" th:remove="tag"]
* "[(${module.path})]"
[/]

[# th:if="(${not #strings.isEmpty(state.project.preconditionBuild)})"]
The README names the following preconditions for build:

"[(${state.project.preconditionBuild})]"
[/]

[# th:if="(${not #strings.isEmpty(state.project.preconditionRun)})"]
The README names the following preconditions for execution:

"[(${state.project.preconditionRun})]"
[/]

Here is a table that maps each programming languages to a wildcard expression:

|Programming language |Wildcard Expression |
|---------------------|--------------------|
|Java                 | *.java             |
|Kotlin               | *.kt               |
|Javascript           | *.js               |
|Typescript           | *.ts               |
|C++                  | *.(h|hpp|cpp|cc)   |
