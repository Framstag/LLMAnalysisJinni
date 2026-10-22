# Read only the build file of the module

## Why

The analysis of the build files of a module asks the model to read the build file of the module and
to find the build files of the directories below it, so that the build files of the submodules are
known as well.

The module list already names every module of the project with its path. The build files of the
modules below are therefore known before the analysis starts, and every module gets its own step
that reads its own build file.

The cost of the redundant reads is large. In the analysis of the Maven project the build file task
made 723 tool calls, 515 of them reads of a build file. Only 111 of those reads were of a build
file at the top level of a module; 404 read a build file of a module below, such as
`its/core-it-support/core-it-plugins/pom.xml`, 44 times.

Those reads are what makes the step expensive. The heaviest steps of the task hold 19 model rounds
and send 2,970,917 characters, and they are the steps that read the most build files. The task
sends 33,677,177 characters, 47% of the whole analysis, and it is the largest single consumer of
tokens.

## What Changes

- The analysis of a module reads the build file of that module and does not read the build files of
  the modules below it.
- The analysis does not get the tool that lists the files of a directory, because it has no use for
  it any more.

## Impact

- The capability `build-file-identification` loses the requirement that gives the analysis the tool
  that lists the files of a directory.
- The capability `build-file-identification` states that the analysis reads only the build file of
  the module.
- The task `ModuleBuildfileAnalysis` drops the tool from its whitelist, and its prompt drops the
  step that looks for the build files below the module.
