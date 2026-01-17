## Current Goal

Locate all directories representing build modules

## Facts

The project name is: "{{project.name}}"

{{> macros/list_of_build_systems.md}}

{{> facts/build_system_wildcards.md}}

## Solution strategy

* Use the "GetMatchingFilesInDirRecursively" tool using the wildcards for the used build tools to scan the project directory for
source code modules by searching for the relevant build system files.
* From the location of these files deduct the individual build modules directory root.
* Build files may be positioned not only in the module root, but optionally also in subdirectories. Make sure you still list  
  the module root directory in this case.
* Return the list of directories representing a source code module together with an optional name for the module.

## Hints

* Return the directory, where the build files are located, as a module path.
* The name of the module can get extracted from the directory name of the module.
  In the case of the root directory holding build files, you should use the project name as a module name.
* The code may use multiple build systems. In this case return each module directory only once.
* Possible module structures:
* * A single-module project with build files only in the project root directory
* * A plain list of submodules in the root directory 
* * A hierarchical directory and module structure.  
* Mark the root module in the response. 
* You must not read individual build files in this stage!


