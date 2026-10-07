## Current Goal

Load the located SBOM file to make it available for further tool requests.

## Facts

The path of the SBOM file is '{{sbom.path}}'.
Whether an SBOM was located is recorded as '{{sbom.found}}'.

## Solution strategy

* If an SBOM was located, load it using the given SBOMTool.
* If no SBOM was located, do not call the load tool. Answer with the result 'ERROR' and a reason that
  states that the project contains no SBOM, so no dependency data is available for the further analysis.

## Hints

* Load the located SBOM using the 'sbom_load_from_file' tool.
* An empty path means that no SBOM was found. That is a result of the analysis and not a defect of the
  run: the tasks that use dependency data continue with an explicitly empty result and a reason.
* A tool call without a path cannot load anything and answers that the input is missing.
