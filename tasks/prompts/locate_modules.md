## Current Goal

Locate the directories representing build modules

## Facts

The project name is: [(${state.project.name})]

The following build systems are used:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
* Build System: '[(${buildsystem.name})]', variant: '[(${buildsystem.variant})]'
[/]

|Build System |Variant     |Wildcard Expression |
|-------------|------------|--------------------|
|Maven        |            | "pom.xml"          |
|Gradle       | Classic    | "*.grade"          |
|Gradle       | Kotlin DSL | "*gradle.kts"      |

## Solution strategy

* Use the "FileCountPerFileTypeAndDirectory" tool to scan the project directory for
source code modules by searching for build system files and their location in the directory structure.
* Build files may be positioned not only in the module root, but in subdirectories. Make sure you still list  
  the module root directory in this case.
* Return the list of directories representing a source code module together with an optional name for the module.

## Hints

* Return the directory, where the build files are located, as module path.
* The name of the module can get extracted from the directory name of the module.
  In case of the root directory, you should use the project name as module name.
* The code may use multiple build systems. In this case return each module directory only once.
* Module structure may be either a plain list of submodules in the root directory or a common subdirectory 
  or - less likely -  might use a hierarchical directory structure.  
* Mark the root module in the response. 


