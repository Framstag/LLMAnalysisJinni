## Current Goal

* Based on the loaded SBOM, I expect from you a list of all used lincenses.
* I also expect an analysis of the used licenses regarding at least the following aspects:
* * Can licenses be used in combination?
* * Are there any restrictions regarding the use of the licenses?
* * Can the licenses be used in a free environment?
* * Can the licenses be used in a commercial environment?
* * Do I have to implement certain additional restrictions to comply with the licenses?

## Facts

The path of the SBOM file is '{{sbom.path}}'.

## Solution strategy

* Check if the SBOM is already loaded.
* If not, load it from the given file.
* Initiate generation of reports regarding licenses by calling the tool 'SBOMWriteLicenseReports'.
* Get a list of Licenses by calling the SBOM tool
* Identify the licenses, evaluate their usage regarding possible restrictions and their use in a free or commercial environment.
* Provide a comprehensive assessment, including reasoning, evaluation and description of possible problems concerning the combined application of the identified licenses.

## Hints

* There is an SBOM tool that lists you all licenses found in the SBOM of all direct and transitive
  dependencies.
* DO NOT try to identify dependencies by scanning directory contents or individual files at this point!
* DO NOT call the 'GetAllFilesInDir' tool or a similar tool!
* Accept that the list of licenses returned from the tool may be empty and continue with an empty list and an appropriate reason.
* Return the licenses return from the tool call 1:1 in your response.
