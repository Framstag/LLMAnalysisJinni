## Current Goal

* I want a short description for each code module found before.

## Facts

The following build systems have been identified:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
* [(${buildsystem.name})]
  [/]

The following build modules have been identified:

[# th:each="module : ${state.modules.modules}" th:remove="tag"]
* "[(${module.path})]"
  [/]

The following programming languages have been identified:

[# th:each="language : ${state.programming_languages.programming_languages}" th:remove="tag"]
* [(${language.name})]
  [/]

From the README we have extracted the following general project description:

"[(${state.project.goal})]"

## Solution strategy

* Use the "GetFilesOverview" Tool (possibly repeatedly) to scan each module directory.
* Analyse the purpose of each module based on the file names and their structure.
* Return a list of modules together with their description

## Hints

* You can iteratively use the "GetFilesOverview" tool using wildcards specific for a programming language.
* Use a call for each module root.
* Make use you use a high value for "depth" to make sure you will see the code files.
* Analyse the given facts, to make a good guess, which programming languages might be used
to avoid scanning for all of them.

Here is a table that allow you to mak programming languages to glob expression

|Programming language|Glob Expression  |
|--------------------|-----------------|
|Java                | *.java          |
|Kotlin              | *.kt            |
|Javascript          | *.js            |
|Typescript          | *.ts            |
|C++                 | *.(h|hpp|cpp|cc)|

Here is a table to map build systems to glob expressions:

|Build System|Glob Expression|
|------------|---------------|
|Maven       | {,**/}pom.xml |
|Gradle      | {,**/}*.grade |

* You must follow the given JSON response structure in your response in any case.

