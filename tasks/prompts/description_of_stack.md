## Current Goal

Now that we have a list of technologies used based on the list of dependencies
try to describe the used technology stack in relation to common technology stacks.

## Facts

The path of the SBOM file is '[(${state.sbom.path})]'.

## Solution strategy

* Check, if the SBOM ist already loaded.
* If not, load it from the given file.
* Get the list of dependencies from the loaded SBOM
* Based on the list of dependencies identify the used the technology stack.

## Hints

* You can get the list of dependencies using the 'SBOMApplicationDependencies' tool.
* Your description should be split into two paragraphs.
* In the first paragraph you match the stack to one of your known technology stacks.
* This paragraph should be a free form description.
* In the second paragraph you should state derivation from this stack. Derivations could
  be elements left out or added which are normally not part of the stack.
* The second paragraph should be a list of derivations, where each bullet point states one
  derivation.
