## Current Goal

Based on the loaded SBOM I expect from you a summary of all used licenses.
I also expect an analysis, if any of the licenses are critical to use in a commercial
environment.

## Facts

The path of the SBOM file is '[(${state.sbom.path})]'.

## Solution strategy

* Check, if the SBOM ist already loaded.
* If not, load it from the given file.
* Get a list of Licenses by calling the SBOM tool, identify the licenses are return the evaluation summary afterward.

## Hints

* There is SBOM tool that lists you all licenses found in the SBOM of all direct and transitive
  dependencies.
* DO NOT try to identify dependencies by scanning directory contents or individual files at this point!
* DO NOT call the 'GetAllFilesInDirRecursively' or 'GetAllFilesInDir' tool or a similar tool!
* Accept that the list of licenses may be empty and continue with an empty list and an appropriate reason.
