## Current Goal

Get the list of used dependencies by accessing data in the previously located and loaded SBOM file by querying the used dependencies.

## Facts

The path of the SBOM file is '{{sbom.path}}'.

## Solution strategy

* If the SBOM is not already loaded, but an SBOM exists, load it via the tool call 'sbom_load_from_file' before further analysis.
* Get the list of dependencies from the SBOM by calling the 'sbom_get_application_dependencies' tool.
Transform the dependencies to the requested output format.

## Hints

* The tool result carries the dependencies, the `route` the read took, the `reason` the read reported, the number
  of components the SBOM holds and the number of components that are modules of the project itself.
* Read the `route` before you call the dependencies the dependencies of the application:
  * `root`: the read found the entry of the root component, so the list is the direct dependency list of the
    application.
  * `project-modules`: the SBOM describes an aggregator build and holds no entry for the root component, so the list
    is what the project modules depend on. Report the list, and say in the reasoning that it was read from the
    project modules and not from the root component.
  * `components`: the SBOM holds no usable dependency graph. Report the list, and say in the reasoning that it holds
    the components of the SBOM and that it does not tell a direct dependency from a transitive one. Do not report
    such a list as the direct dependencies of the application.
  * `none`: no SBOM is loaded.
* Report the components the result left out as modules of the project in `reasoning`, instead of listing them as
  dependencies.
* When the list is empty, continue with an empty list and report the `reason` the tool stated. Do not state a cause
  of the empty list that the tool did not report.
