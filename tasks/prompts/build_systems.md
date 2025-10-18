## Current Goal

* I want you to name the build systems used in the project.
* Return a list of all build systems you have identified.

## Facts

The following table shows wildcards matching the specific files for the given build system:

| Build System | Variant    | Wildcard Expression |
|--------------|------------|---------------------|
| Maven        |            | "pom.xml"           |
| Gradle       | Classic    | "*.gradle"          |
| Gradle       | Kotlin DSL | "*.gradle.kts"      |

## Solution strategy

* To identify a build system used in the project you should iteratively search
  for files that are representative for a given build system.

## Hints

* To look fort certain files use the 'FileCountPerFileType' tool. Pass to it the root directory and a list of wildcard expression matching files of the various build systems.
