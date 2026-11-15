## Current Goal

Now that we have a list of technologies used based on the list of dependencies
try to describe the used technology stack in relation to common technology stacks.

## Facts

The path of the SBOM file is '{{sbom.path}}'.

## Solution strategy

* Check if the SBOM is already loaded.
* If not, load it from the given file.
* Get the list of dependencies from the loaded SBOM
* Based on the list of dependencies, identify the used the technology stack.

## Hints

* You can get the list of dependencies using the 'sbom_get_application_dependencies' tool.
* Your description should be split into two paragraphs.
* In the first paragraph you match the stack to one of your known technology stacks.
* This paragraph should be a free form description.
* In the second paragraph you should state derivation from this stack. Derivations could
  be elements left out or added which are normally not part of the stack.
* The second paragraph should be a list of derivations, where each bullet point states one
  derivation.
* When you get the list of dependencies, read the `route` and the `reason` of the tool result. When the route is not
  `root`, say in your first paragraph that the dependencies were read from the project modules of an aggregator build
  or from the components of the SBOM, and that the list is therefore not the direct dependency list of the root
  component.
* Accept that the list of dependencies may be empty and continue with an empty list and the `reason` the tool stated.
  Do not state a cause of the empty list that the tool did not report.
