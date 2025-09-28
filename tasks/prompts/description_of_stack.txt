## Current Goal

Now that we have a list of technologies used based on the list of dependencies
try to description the used technology stack in relation to common technology stacks.

## Solution strategy

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
