## Current Goal

Locate all directories representing build modules

## Facts

The project name is: "{{project.name}}"

{{> macros/list_of_build_systems.md}}

{{> facts/build_system_wildcards.md}}

## Solution strategy

* Use the 'filesystem_get_matching_files_in_dir_recursively' tool for the root directory ('') using the wildcards for the used build tools to scan the project directory for build modules by searching for the relevant build system files.
* From the location of these files deduct the individual build modules directory root.
* Return the list of directories representing a source code module together with an optional name for the module.
* Make sure that each module has a unique name. 
In case of nested modules concat the names with a "." in the hierarchy to get a globally unique name.
* The name of the module should not contain whitespaces or similar. Replace these characters
with '_'.

## Examples for possible structures

### Only root module

Structure:

* MainModule

Result:

* MainModule

### Root and direct submodules

Structure:

* MainModule
* * Submodule1
* * Submodule2
* * Submodule3

Result:

* MainModule
* MainModule.Submodule1
* MainModule.Submodule2
* MainModule.Submodule3

### Partially Nested modules

Structure:

* MainModule
* * Submodule1
* * * Subsubmodule1
* * * Subsubmodule2
* * Submodule2
* * * Subsubmodule1
* * * Subsubmodule2
* * * Subsubmodule3
* * Submodule3

Result:
* 
* MainModule
* MainModule.Submodule1
* MainModule.Submodule1.Subsubmodule1
* MainModule.Submodule1.Subsubmodule2
* MainModule.Submodule2
* MainModule.Submodule2.Subsubmodule1
* MainModule.Submodule2.Subsubmodule2
* MainModule.Submodule2.Subsubmodule3
* MainModule.Submodule3


## Hints

* Build files may be positioned not only in the module root but optionally also in subdirectories. Make sure you also list  
  the module root directory in this case.
* Not all module may contain code. 
Also return these modules if they are part of the build structure.
* Return the directory, where the build files are located, as a module path.
* The name of the module can get extracted from the directory name of the module.
In the case of the root directory holding build files, you should use the project name as a module name.
* The code may use multiple build systems. In this case return each module directory only once.
* Mark the root module in the response. Explicitly state this with a 'true' or 'false'. If you are unsure, return a 'false'.
* Ignore modules that appear to have been downloaded, have been created during the build or similar or are placed in directories that clearly hold build products or are not actively developed as part of the project.
