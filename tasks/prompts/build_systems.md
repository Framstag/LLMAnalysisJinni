## Current Goal

* I want you to name the build systems used in the project.
* Return a list of all build systems you have identified.

## Facts

[# th:insert="~{facts/build_system_wildcards}" /]

## Solution strategy

* To identify a build system used in the project you should iteratively search
  for files that are representative for a given build system.

## Hints

* To look fort certain files use the 'FileCountPerFileType' tool. Pass to it the root directory and a list of wildcard expression matching files of the various build systems.
