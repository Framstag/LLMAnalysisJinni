## Current Goal

Get the list of used technologies from the loaded SBOM file.

## Solution strategy

* Get the list of dependencies from the SBOM using the 'SBOMApplicationDependencies' tool.
* Return the list of dependencies by transforming the result to the requested output format.

## Hints

* To gather the required information, we evaluate the content of the generated SBOM file, which lists
  all direct and transitive dependencies.
* Read the list of application dependencies by calling the 'SBOMApplicationDependencies' tool.

