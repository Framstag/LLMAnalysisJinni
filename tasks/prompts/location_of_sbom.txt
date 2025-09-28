## Current Goal

We assume that a SBOM file was generated during project build.
We want to identify this file below the project directory.

## Solution strategy

* Locate the SBOM using repeated calls to the 'FindFilesMatching' Tool.
* Load the located SBOM using the 'LoadSBOMFromFile' tool.

## Hints

* We expect the SBOM to be placed somewhere in the project directory.
* We need to locate it in the filesystem. The file normally is a
   json file with a corresponding "*.json" file postfix. The filename or the directory thus should
    contain the name "bom" or "sbom" in some form.
* You can search for possible candidates yourself using the 'FindFilesMatching' tool.
* You should  try one or more of the following wildcard expressions in the given order until you find a SBOM:
  * '**/sbom.json'
  * '**/*bom*/*.json'
* Select the best match, based on directory and file names.
