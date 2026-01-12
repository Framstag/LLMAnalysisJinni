## Current Goal

Based on the loaded SBOM I expect from you a summary of all used licenses.
I also expect an analysis if any of the licenses are critical to use in a commercial
environment.

## Facts

The path of the SBOM file is '{{sbom.path}}'.

## Solution strategy

* Check if the SBOM is already loaded.
* If not, load it from the given file.
* Initiate generation of reports regarding licenses by calling the tool 'SBOMWriteLicenseReports'.
* Get a list of Licenses by calling the SBOM tool
* Identify the licenses, evaluate their usage regarding possible restrictions and their use in a free or commercial environment.
* Return the list of identified licenses and their evaluation (summary, problems, reasoning) in your response.

## Hints

* There is an SBOM tool that lists you all licenses found in the SBOM of all direct and transitive
  dependencies.
* DO NOT try to identify dependencies by scanning directory contents or individual files at this point!
* DO NOT call the 'GetAllFilesInDirRecursively' or 'GetAllFilesInDir' tool or a similar tool!
* Accept that the list of licenses may be empty and continue with an empty list and an appropriate reason.
