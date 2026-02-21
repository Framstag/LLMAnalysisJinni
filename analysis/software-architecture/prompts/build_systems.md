## Current Goal

* I want you to name the build systems used in the project.
* Return a list of all build systems you have identified.

## Facts

{{> facts/build_system_wildcards.md}}

## Solution strategy

* To identify a build system used in the project, you should iteratively search
  for files that are representative for a given build system.

## Hints

* To look for certain files, use the 'GetMatchingFilesInDirRecursively' tool. Pass to it the root directory and a list of wildcard expressions matching files of the various build systems.
* Make sure that you only take into account files that are active project files.
* Thus ignore files found, that act as examples, demos or that are placed within build directories, belong to build artifacts or build dependencies, are generated or similar.
* Thus ignore files found, that act as examples, demos or that are placed within build directories, belong to build artifacts or build dependencies, are generated or similar.
* Analyze the directory these files are found in to make such a judgment.
* List only build systems in the response that were actually detected. Do not list build systems that were not found in the project.
