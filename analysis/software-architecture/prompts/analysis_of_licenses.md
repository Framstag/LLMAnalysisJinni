## Current Goal

* Based on the loaded SBOM, I expect from you a list of all used licenses.
* I also expect an analysis of the used licenses regarding at least the following aspects:
* * Can licenses be used in combination?
* * Are there any restrictions regarding the use of the licenses?
* * Can the licenses be used in a free environment?
* * Can the licenses be used in a commercial environment?
* * Do I have to implement certain additional restrictions to comply with the licenses?

## Facts

The path of the SBOM file is '{{sbom.path}}'.

## Solution strategy

* If the SBOM is not already loaded, but an SBOM exists, load it via the tool call 'sbom_load_from_file' before further analysis.
* Initiate generation of reports regarding licenses by calling the tool 'sbom_write_license_reports'.
* Get a list of Licenses by calling the SBOM tool
* Identify the licenses, evaluate their usage regarding possible restrictions and their use in a free or commercial environment.
* Provide a comprehensive assessment, including reasoning, evaluation and description of possible problems concerning the combined application of the identified licenses.

## Hints

* There is an SBOM tool that lists you all licenses found in the SBOM of all direct and transitive
  dependencies.
* Accept that the list of licenses returned from the tool may be empty and continue with an empty list and an appropriate reason.
* Return the licenses return from the tool call 1:1 in your response.
