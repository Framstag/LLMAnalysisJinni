## Current Goal

* Most applications have some kind of README containing project information in the root directory
of the project. I want you to locate this README and returns is file name to me.

## Solution strategy

* Look for such files the root directory of the project.
* Return the most likely filename.

## Hints

* You can call the 'GetFilesOverview' tool function to scan for READMEs.
* Start from the root directory ('') using "*" as a wildcard to see all files and depth = 0 to only see files in the root directory itself.
