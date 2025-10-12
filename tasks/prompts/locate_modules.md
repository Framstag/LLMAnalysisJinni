## Current Goal

Locate the directories representing build modules

## Solution strategy

* Use the "FindMatchingFiles" tool (possibly repeatedly) to scan the project directory for
source code modules by searching for build system files and their location in the directory structure.
* Return the list of directories representing a source code module together with an optional name for the module.

## Facts

The project name is: [(${state.project.name})]

The following build systems are used:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
 * [(${buildsystem.name})]
[/]

Here is a table to map build systems to glob expressions for finding build system files:

|Build System|Glob Expression  |
|------------|-----------------|
|Maven       | "{,**/}pom.xml" |
|Gradle      | "{,**/}*.grade" |

## Hints

* A source code module in general is equal to a build system build unit.
* Search for build system files to detect build modules.
* Results without an explicit path are located in the root directory of the project.
* Return the directory, where the build files are located, as module path
* The name of the module can get extracted from the directory name of the module.
  In case of the root directory, you should use the project name as module name.
* The code may use multiple build systems. In this case return each module directory only once.


