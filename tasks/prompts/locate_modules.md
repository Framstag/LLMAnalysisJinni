## Current Goal

Locate alle directories representing build modules

## Facts

The project name is: [(${state.project.name})]

The following build systems are used:

[# th:each="buildsystem : ${state.build.buildsystems}" th:remove="tag"]
* Build System: '[(${buildsystem.name})]', variant: '[(${buildsystem.variant})]'
[/]

The following table shows wildcards matching the specific files for the given build systems:

| Build System | Variant    | Wildcard Expression |
|--------------|------------|---------------------|
| Maven        |            | "pom.xml"           |
| Gradle       | Classic    | "*.gradle"          |
| Gradle       | Kotlin DSL | "*.gradle.kts"      |

## Solution strategy

* Use the "GetMatchingFilesInDirRecursively" tool using the wildcards for the used build tools to scan the project directory for
source code modules by searching for the relevant build system files.
* From the location of these files deduct the individual build modules directory root.
* Build files may be positioned not only in the module root, but optionally also in subdirectories. Make sure you still list  
  the module root directory in this case.
* Return the list of directories representing a source code module together with an optional name for the module.

## Hints

* Return the directory, where the build files are located, as module path.
* The name of the module can get extracted from the directory name of the module.
  In case of the root directory holding build files, you should use the project name as module name.
* The code may use multiple build systems. In this case return each module directory only once.
* Possible module structures:
* * A single-module project with a build files only in the project root directory
* * A plain list of submodules in the root directory 
* * A hierarchical directory and module structure.  
* Mark the root module in the response. 
* You must not use the "GetAllFilesInDirRecursively" tool,since this will not perform on larger projects!
* You must not read individual build files in this stage!


