## Current Goal

* I want you to name the build systems used in the project.
* Return a list of all build systems you have identified.

## Facts

{{> facts/build_system_wildcards.md}}

## Solution strategy

* To identify a build system used in the project, you should iteratively search
  for files that are representative for a given build system.

## Hints

* To look for certain files, use the 'FileCountPerFileType' tool. Pass to it the root directory and a list of wildcard expressions matching files of the various build systems.
