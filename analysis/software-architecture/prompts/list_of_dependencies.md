## Current Goal

Get the list of used dependencies by accessing data in the previously located and loaded SBOM file by querying the used dependencies.

## Facts

The path of the SBOM file is '{{sbom.path}}'.

## Solution strategy

* If the SBOM is not already loaded, but an SBOM exists, load it via the tool call 'sbom_load_from_file' before further analysis.
* Get the list of dependencies from the SBOM by calling the 'sbom_get_application_dependencies' tool.
Transform the dependencies to the requested output format.

## Hints

* Accept that the list of dependencies may be empty and continue with an empty list and an appropriate reason.
