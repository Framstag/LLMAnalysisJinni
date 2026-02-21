## Current Goal

Get the list of used dependencies by accessing data in the previously located and loaded SBOM file by querying the used dependencies.

## Facts

The path of the SBOM file is '{{sbom.path}}'.

## Solution strategy

* If the SBOM is not already loaded, but an SBOM exists, load it before further analysis.
* Get the list of dependencies from the SBOM by calling the 'SBOMApplicationDependencies' tool.
Transform the dependencies to the requested output format.

## Hints

* DO NOT try to identify dependencies by scanning directory contents or individual files at this point!
* DO NOT call the 'GetAllFilesInDir' tool or a similar tool!
* Accept that the list of dependencies may be empty and continue with an empty list and an appropriate reason.
