## Current Goal

We assume that a SBOM file was generated during project build.
We want to identify this file below the project directory.

## Facts

{{> facts/sbom_file_names.md}}

## Solution strategy

* Locate the SBOM using a call to the 'GetMatchingFilesInDirRecursively' Tool.
* If no SBOM was found, state so in the response.

## Hints

* We expect the SBOM to be placed somewhere in the project directory.
* You can search for possible candidates yourself using the 'GetMatchingFilesInDirRecursively' tool passing all possible wildcards.
* Select the best match, based on directory and file names.
