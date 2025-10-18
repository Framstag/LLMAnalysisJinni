## Goal

Based on the loaded SBOM I expect from you a summary of all used licenses.
I also expect an analysis, if any of the licences are critical to use in a commercial
environment.

## Facts

The path of the SBOM file is '[(${state.sbom.path})]'.

## Solution strategy

* Check, if the SBOM ist already loaded.
* If not, load it from the given file.
* Get a list of Licenses by calling the SBOM tool, identify the licenses are return the evaluation summary afterward.

## Hints

* There is SBOM tool that lists you all licences found in the SBOM of all direct and transitive
  dependencies.
