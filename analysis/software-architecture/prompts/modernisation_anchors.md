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

* Call `java_get_anchor_candidates` once and read the bounded candidate payload it returns. The tool counts, you judge. It is the only tool of this analysis: the counted evidence behind the anchor types is part of its result, so you call no evidence tool of your own.
* **`java_get_anchor_candidates`** returns one payload with four parts.
* The part `candidatesPerType` is the budget that was applied to every list, taken from the workspace property `anchor.candidatesPerType`.
* The part `anchorTypes` holds one entry per anchor type, the six types that rest on counted evidence. An entry names its `anchorType`, its `budget`, its `totalCount`, that is how many candidates of that type the project holds, its `returnedCount`, that is how many of them the entry lists, and its `candidates`, the strongest candidates of the type up to the budget.
* The part `skipped` holds the modules whose raw Java report is missing, each with the reason. Such a module carries no evidence: do not invent a count for it, and say in the reasoning of the analysis that the module was skipped.
* The part `absentSources` holds the analyses whose input is absent, each with the reason. An anchor type whose source is named there holds no candidate of that type.
* Every candidate carries its `element`, its `module`, its `alsoSeenIn`, its `evidence`, that is the counted evidence it rests on with its numbers, and its `source`, that is the analysis that counted that evidence. Name the `source` of a candidate in the evidence of the anchor you form from it.
* A candidate is reported once per element, even when several module reports hold it, which happens when an aggregator module holds the same classes as its inner modules. Its `module` is the module it is read in and its `alsoSeenIn` names the other modules that hold the same element. Judge the element, not one module view of it: when `alsoSeenIn` is not empty, say in the reasoning of the anchor that the element is not owned by a single module.
* The entry point candidates of production code are ranked above the ones of test and generated code, because a main method of a benchmark harness is not a place a modernisation can attach to. An entry point whose evidence names test or generated code stays in the list, below the production candidates; do not read such an anchor as a product entry, and say in its reasoning what it is.
* The `domain` anchors are not part of the tool result: they rest on the context map result above, which the context map analysis bounded with the budget of its own.
* The input of this analysis has the same shape for a project of any size. No per module evidence of the whole project reaches it: the lists are bounded, and the count of what the project holds beside them is what tells a bounded list apart from a complete one.

### The anchors of every type

* `entry-point`: the candidates of the type `entry-point`, that is the classes with a main method, the classes carrying one of the annotations of the marker facts and the classes that implement one of the service provider interfaces. The evidence of such an anchor names the class, the module, the code category and the annotation or the interface that marks it, and the `source` of the candidate names the entry point analysis as the source of the count.
* `seam`: the candidates of the type `seam`, that is the interfaces of the project. The evidence names the implementor count, the importing modules, the classes of the declaring module that reference the interface without implementing it and the port candidate decision, and the `source` names the seam analysis as the source of the count.
* `domain`: the relations of the context map above. The evidence names both contexts, the relation and the shared language the relation rests on, and it names the context map analysis as the source.
* `build-unit`: the candidates of the type `build-unit`, that is the modules of the project. The evidence names the efferent coupling, the afferent coupling and the instability of the module, and the `source` names the inter module dependency analysis as the source of the counts.
* `cut-point`: the candidates of the type `cut-point`, that is the places where a module can be cut, taken from the separation ladder and the minimum cut of the class dependency graph analysis. The evidence names the module, the cost of the step and the severed weight, and the `source` names the class dependency graph analysis as the source.
* `test-coverage`: the candidates of the type `test-coverage`, that is the modules whose test coverage was counted. The evidence names the coverage values of the module, and the `source` names the test coverage analysis as the source of the counts.
* `technology-marker`: the candidates of the type `technology-marker`. The type has two halves, and every anchor says which half it belongs to. The namespace half is the dependencies whose group or namespace is one of the legacy namespaces of the marker facts; it rests on the namespaces the project's own production code imports, as the import distribution of the anchor candidates counted them. The version range half is the dependencies whose version lies in one of the version ranges at the end of the life of a dependency; it rests on the dependency data of the SBOM. The evidence names the dependency or the namespace, the marker that applies, the version when the dependency data carries one, and the `source` that counted it. When the payload names the dependency analysis in `absentSources`, say that the version range half is unavailable and name the reason it gives.

### The rank, the evidence and the candidate patterns

* The `rank` of an anchor is its position among the anchors of its type in this project, 1 being the anchor that rests on the strongest evidence. It is a rank, not a score, not a threshold and not a verdict. Never state a threshold that would decide the same outcome in another project.
* No anchor is good or bad. An anchor that stands out from its peers is reported, and it is not marked as a defect, a risk or a failure.
* The `evidence` of an anchor holds the counted evidence it rests on, and every entry names the analysis that counted it, so that a reader can find the number behind the anchor.
* The `candidatePatterns` of an anchor hold the names of the patterns of the catalogue that fit it, and nothing else. A pattern that is not in the catalogue is not used. An anchor for which no pattern of the catalogue fits carries an empty list of candidates and the reason in its `reasoning`.
* For every candidate pattern, the `reasoning` of the anchor states what would make that pattern the wrong choice, so that a reader can disagree with the analysis instead of having to unravel it.
* The `reasoning` of an anchor says why the anchor was read as it was.
* Every candidate list of the payload is bounded by the budget `candidatesPerType`. When the `returnedCount` of an anchor type is smaller than its `totalCount`, that list is a part of the candidates of the type and not the whole of them: say in the `reasoning` of the answer that the list was bounded and how many candidates of that type the project holds beyond it, so that a reader knows the rank was formed against a bounded list.

### The anchor types that are absent

* An anchor type whose input analysis did not run is reported in `absentTypes` with the reason. It is not left out of the report.
* The `domain` anchors are absent when the context map did not run or when it holds no relation.
* The `cut-point` anchors are absent when the separation ladder holds no step for any module.
* The `build-unit` anchors are absent when the inter module dependency report holds no module.
* The `test-coverage` anchors are absent when the test coverage report holds no module.
* The two halves of the `technology-marker` type are reported apart and are never reported as one absence: the namespace half is absent only when the candidate payload holds no namespace of the import distribution, and the version range half is absent when the SBOM was not loaded or when the dependency list is empty, with the reason the payload gives. The type as a whole is absent only when both halves are missing.
* An anchor of the namespace half states no version: the namespace evidence carries none, so the evidence of such an anchor names the namespace and the marker and says that the version is unknown.
* The `seam` and the `entry-point` anchors are absent when the Java module reports are missing, so that no class of the project could be counted.
* The `absentTypes` list is empty only when every anchor type is present.

### The limits of the analysis

* The anchors rest on the counted evidence of the Java modules alone. A module that is not a Java module, and a class the parser did not see, is not part of the evidence.
* No change frequency, no age of the code and no ownership is part of this analysis. The history of the project is not read, so an anchor says nothing about how often a place changes or who owns it.
* The marker knowledge is a snapshot of a date, so a namespace that was renamed after that date is not noticed.
* The namespace half of the `technology-marker` anchors rests on the imports of the project and not on its declared dependencies, so it states a technology and no version of it. A dependency the project declares but never imports is not part of that half.

## Hints

* The full evidence of the candidate analyses is written as CSV beside the other reports: the seam rows, the entry point rows, the inter module dependency matrix, the separation ladder, the test coverage and the import diversity. Use the values the payload reported and name a class, an interface or a module as the tool spelled it.
* The order of a candidate list is already a rank within the view of its type. Keep it.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `ModernisationAnchors` response schema.
* Each `anchors[]` entry MUST be a JSON object with the fields `anchorType`, `module`, `element`, `evidence`, `rank`, `candidatePatterns` and `reasoning`. Do NOT use plain strings.
* Each `absentTypes[]` entry MUST be a JSON object with the fields `anchorType` and `reason`.
* The `anchorType` of an entry is one of `entry-point`, `seam`, `domain`, `build-unit`, `cut-point`, `test-coverage`, `technology-marker`.
* The `candidatePatterns` of an anchor hold only the names of the catalogue: `strangler-fig`, `branch-by-abstraction`, `module-extraction`, `anticorruption-layer`, `horizontal-lift`, `retirement`.
* Report at most 8 anchors per anchor type. A longer response is cut off before its JSON is complete, so it is rejected and retried.
* You must only return clean JSON content without any prefix or postfix.
