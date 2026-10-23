## Current Goal

* We want to know where a modernisation of this project could attach, and which of the modernisation patterns fit that place.
* Report the anchors of every one of the seven anchor types: `entry-point`, `seam`, `domain`, `build-unit`, `cut-point`, `test-coverage` and `technology-marker`.
* The strength of an anchor is its RANK among the anchors of its type in this project. It is never a verdict, no anchor is good or bad, and no absolute threshold decides anything.

## Facts

The marker knowledge of the analysis: the namespaces that mark a legacy dependency, the version ranges that mark a dependency at the end of its life, the annotations that mark an entry point, the interfaces that mark a service provider interface, and the catalogue of the modernisation patterns an anchor may be matched against.

{{> facts/modernisation_markers.md}}

The context map of the project:

{{#if contextMap}}
The contexts the language of the project suggests:

{{#contextMap.contexts}}
* Context "{{name}}" is spoken by the modules {{#each modules}}{{this}} {{/each}} and holds the terms {{#each language}}"{{this}}" {{/each}}
{{/contextMap.contexts}}

The relations between the contexts:

{{#contextMap.relations}}
* Relation "{{fromContext}}" -> "{{toContext}}" is a `{{relation}}`. The counted evidence behind it: {{evidence}}. The pair shares a leading language: {{sharedLanguage}}. The pair imports each other: {{structurallyCoupled}}.
{{/contextMap.relations}}
{{else}}
The context map analysis did not run, so this project holds no context map. The `domain` anchors are absent, and you report the anchor type `domain` in `absentTypes` with the reason that the context map did not run.
{{/if}}

## Solution Strategy

* Call the tools below once each and read the counted evidence they return. The tools count, you judge.
* A module that a tool names as skipped carries no evidence. Do not invent a count for it, and say in the reasoning of the analysis that the module was skipped.
* **`java_get_entry_points`** counts, per class of every Java module, the module, the code category, whether the class declares a main method, the names of the annotations the class carries and the interfaces it implements. The tool does not say whether an annotation or an interface marks an entry point: that is decided with the marker facts above.
* **`java_get_seams`** counts, per interface of the project, the module that declares it, the classes that implement it with their modules, the classes of the declaring module that reference it without implementing it, the modules of the project that import it and whether the interface is a port candidate.
* **`java_get_inter_module_dependency_report`** returns, per module, the efferent coupling to the other modules, the afferent coupling and the instability, and it writes the full dependency matrix as a CSV report.
* **`java_get_all_class_dependency_graphs`** returns, per module, the class reference graph with its node and edge counts and its heaviest couplings.
* **`java_get_all_split_candidates`** returns, per module, the separation ladder with the cost of every step and the minimum cut of the module.
* **`java_get_all_test_coverage_reports`** returns, per module, the distribution of the test coverage of its classes.
* **`java_get_all_import_diversity_reports`** returns, per module, the distribution of the import diversity, the number of distinct types the classes of the module work with.
* **`sbom_get_application_dependencies`** returns the direct dependencies of the application with their versions. When the tool reports that no SBOM is loaded, the `technology-marker` anchors are absent and you say so.

### The anchors of every type

* `entry-point`: the classes the entry point tool counts as candidates, that is the classes with a main method, the classes carrying one of the annotations of the marker facts and the classes that implement one of the service provider interfaces. The evidence of such an anchor names the class, the module, the code category and the annotation or the interface that marks it, and it names the entry point analysis as the source of the count.
* `seam`: the interfaces the seam tool counts. The evidence names the implementor count, the importing modules, the classes of the declaring module that reference the interface without implementing it and the port candidate decision, and it names the seam analysis as the source of the count.
* `domain`: the relations of the context map above. The evidence names both contexts, the relation and the shared language the relation rests on, and it names the context map analysis as the source.
* `build-unit`: the modules the inter module dependency report counts. The evidence names the efferent coupling, the afferent coupling and the instability of the module, and it names the inter module dependency analysis as the source of the counts.
* `cut-point`: the places where a module can be cut, taken from the separation ladder and the minimum cut of the class dependency graph analysis. The evidence names the module, the cost of the step and the severed weight, and it names the class dependency graph analysis as the source.
* `test-coverage`: the modules whose test coverage the report counts. The evidence names the coverage values of the module and the analysis that counted them.
* `technology-marker`: the dependencies of the application that the marker facts mark, that is the dependencies whose group or namespace is one of the legacy namespaces, and the dependencies whose version lies in one of the version ranges at the end of the life of a dependency. The evidence names the dependency, its version, the marker that applies and the dependency analysis that counted it.

### The rank, the evidence and the candidate patterns

* The `rank` of an anchor is its position among the anchors of its type in this project, 1 being the anchor that rests on the strongest evidence. It is a rank, not a score, not a threshold and not a verdict. Never state a threshold that would decide the same outcome in another project.
* No anchor is good or bad. An anchor that stands out from its peers is reported, and it is not marked as a defect, a risk or a failure.
* The `evidence` of an anchor holds the counted evidence it rests on, and every entry names the analysis that counted it, so that a reader can find the number behind the anchor.
* The `candidatePatterns` of an anchor hold the names of the patterns of the catalogue that fit it, and nothing else. A pattern that is not in the catalogue is not used. An anchor for which no pattern of the catalogue fits carries an empty list of candidates and the reason in its `reasoning`.
* For every candidate pattern, the `reasoning` of the anchor states what would make that pattern the wrong choice, so that a reader can disagree with the analysis instead of having to unravel it.
* The `reasoning` of an anchor says why the anchor was read as it was.

### The anchor types that are absent

* An anchor type whose input analysis did not run is reported in `absentTypes` with the reason. It is not left out of the report.
* The `domain` anchors are absent when the context map did not run or when it holds no relation.
* The `cut-point` anchors are absent when the separation ladder holds no step for any module.
* The `build-unit` anchors are absent when the inter module dependency report holds no module.
* The `test-coverage` anchors are absent when the test coverage report holds no module.
* The `technology-marker` anchors are absent when the SBOM was not loaded or when the dependency list is empty.
* The `seam` and the `entry-point` anchors are absent when the Java module reports are missing, so that no class of the project could be counted.
* The `absentTypes` list is empty only when every anchor type is present.

### The limits of the analysis

* The anchors rest on the counted evidence of the Java modules alone. A module that is not a Java module, and a class the parser did not see, is not part of the evidence.
* No change frequency, no age of the code and no ownership is part of this analysis. The history of the project is not read, so an anchor says nothing about how often a place changes or who owns it.
* The marker knowledge is a snapshot of a date, so a namespace that was renamed after that date is not noticed.

## Hints

* The full evidence is written as CSV beside the other reports: the seam rows, the entry point rows, the inter module dependency matrix, the separation ladder, the test coverage and the import diversity. Use the values the tool reported and name a class, an interface or a module as the tool spelled it.
* The order the tools return is already a rank within their own view. Keep it.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `ModernisationAnchors` response schema.
* Each `anchors[]` entry MUST be a JSON object with the fields `anchorType`, `module`, `element`, `evidence`, `rank`, `candidatePatterns` and `reasoning`. Do NOT use plain strings.
* Each `absentTypes[]` entry MUST be a JSON object with the fields `anchorType` and `reason`.
* The `anchorType` of an entry is one of `entry-point`, `seam`, `domain`, `build-unit`, `cut-point`, `test-coverage`, `technology-marker`.
* The `candidatePatterns` of an anchor hold only the names of the catalogue: `strangler-fig`, `branch-by-abstraction`, `module-extraction`, `anticorruption-layer`, `horizontal-lift`, `retirement`.
* Report at most 8 anchors per anchor type. A longer response is cut off before its JSON is complete, so it is rejected and retried.
* You must only return clean JSON content without any prefix or postfix.
