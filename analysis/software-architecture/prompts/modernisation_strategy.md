## Current Goal

* We want to know which places of this project a modernisation could attach to, which of the modernisation patterns fit each of them, and what the change of such a place would cost.
* Report one modernisation area per candidate place, with the patterns that fit it, the evidence for and against every one of them, and the figures the cost rests on.
* The order of the areas follows the counted cost. It is not a decision, and no area is named as the one to start with.

## Facts

The marker knowledge of the analysis: the namespaces that mark a legacy dependency, the version ranges that mark a dependency at the end of its life, the annotations that mark an entry point, the interfaces that mark a service provider interface, and the catalogue of the modernisation patterns an area may be matched against.

{{> facts/modernisation_markers.md}}

## The inputs of the analysis

The inputs are the anchor result, the hotspot result and the counted evidence. The prose findings of the other evaluation analyses are not read, so that the size of the input does not grow with the number of findings of the project.

{{#if modernisation_anchors}}
The anchors of the project:

{{#modernisation_anchors.anchors}}
* Anchor {{rank}} of the type `{{anchorType}}`: the module "{{module}}", the element "{{element}}". The counted evidence behind it: {{#each evidence}}{{this}} {{/each}}The candidate patterns of the anchor: {{#each candidatePatterns}}{{this}} {{/each}}The reasoning of the anchor: {{reasoning}}
{{/modernisation_anchors.anchors}}
{{#modernisation_anchors.absentTypes}}
* The anchor type `{{anchorType}}` is absent in the anchor result: {{reason}}
{{/modernisation_anchors.absentTypes}}
{{else}}
The anchor analysis did not run, so the project holds no anchor. The areas that rest on the anchors are absent, and you report the type `anchor` in `absentTypes` with the reason that the anchor analysis did not run.
{{/if}}

{{#if hotspotEvaluation}}
{{#if hotspotEvaluation.hotspots}}
The counted hotspots of the project:

{{#hotspotEvaluation.hotspots}}
* Hotspot {{rank}}: the class "{{className}}" appears in the modules {{#each modules}}{{this}}{{#unless @last}}, {{/unless}}{{/each}}; it changed {{changeCount}} times and carries a counted complexity of {{complexity}} over {{methodCount}} methods.
{{/hotspotEvaluation.hotspots}}
{{else}}
The hotspot analysis ran, but it holds no counted hotspot. No area rests on a hotspot, and you report the type `hotspot` in `absentTypes` with the reason that the hotspot analysis holds no counted hotspot.
{{/if}}
{{else}}
The hotspot analysis did not run, so the project holds no counted hotspot. The areas that rest on the hotspots are absent, and you report the type `hotspot` in `absentTypes` with the reason that the hotspot analysis did not run.
{{/if}}

## Solution Strategy

* Call `java_get_anchor_candidates` once and read the bounded candidate payload it returns as the counted evidence of the cost. The tool counts, you judge. It is the only tool of this analysis: the counted evidence of the modules is part of its result, so you call no evidence tool of your own.
* **`java_get_anchor_candidates`** returns one payload with four parts.
* The part `candidatesPerType` is the budget that was applied to every list, taken from the workspace property `anchor.candidatesPerType`.
* The part `anchorTypes` holds one entry per anchor type. An entry names its `anchorType`, its `budget`, its `totalCount`, that is how many candidates of that type the project holds, its `returnedCount`, that is how many of them the entry lists, and its `candidates`, the strongest candidates of the type up to the budget. Among the types are the `build-unit` candidates, that is the coupling hubs and the coupling orphans of the project, the `cut-point` candidates, that is the separation ladder with the price of every step, the `test-coverage` candidates, that is the coverage of every module, and the `seam`, `entry-point` and `technology-marker` candidates.
* Every candidate carries its `element`, its `module`, its `evidence`, that is the counted evidence it rests on with its numbers, and its `source`, that is the analysis that counted that evidence. The afferent coupling of a module, the price of a cut and the test coverage of a module are read from the evidence of the candidate of the matching type, so every figure of the cost of an area names the counted evidence and the `source` it was read from.
* The part `skipped` holds the modules whose raw Java report is missing, each with the reason. Such a module carries no evidence: do not invent a count for it, and name the figure of that module as missing in the area.
* The part `absentSources` holds the analyses whose input is absent, each with the reason. An anchor type whose source is named there holds no candidate of that type.
* Every candidate list of the payload is bounded by the budget `candidatesPerType`. When the `returnedCount` of an anchor type is smaller than its `totalCount`, that list is a part of the candidates of the type and not the whole of them, so a cost that rests on such a list is named as resting on a bounded list in the area, with how many candidates of that type the project holds beyond it.
* The input of this analysis has the same shape for a project of any size: no per module evidence of the whole project reaches it.

### The modernisation areas

* One area per candidate place. The candidates are the anchors of the anchor result and the counted hotspots of the project.
* An anchor of the anchor result becomes an area. The area names the module of the anchor, the element the anchor points at, and the anchor type the area rests on. An anchor that points at no element, such as a build unit anchor, becomes an area of its module and carries an empty element.
* A hotspot class that is the element of an anchor belongs to the area of that anchor, and it gives that area the evidence of the churn of the code: the change count of the file and the counted complexity of the class.
* A hotspot class that no anchor names is a candidate place of its own. The area names the module of the class, the class as its element, and the anchor type `build-unit`, because the build unit of the module is the anchor the area rests on.
* The patterns of an area come from the catalogue of the marker facts and from the candidate patterns of the anchor the area rests on. A pattern that the catalogue does not hold is not named. An area for which no pattern of the catalogue fits carries an empty list of patterns and the reason in its reasoning.
* For every pattern of an area, the `evidenceFor` holds the counted evidence that supports the pattern for this area, and every entry names the analysis that counted it. The `evidenceAgainst` holds the counted evidence that counts against the pattern for this area, and every entry names the analysis that counted it. When no evidence counts against a pattern, the `evidenceAgainst` is empty, and the reasoning of the area says that no evidence against that pattern was found.
* The `whatWouldMakeItWrong` of a pattern names what would have to be true for the pattern to be the wrong choice for this area, so that a reader can disagree with the analysis instead of having to unravel it.
* The `costFigures` of an area hold the figures the cost of the area rests on. Every figure carries its name, its value as the counted analysis reports it, and its source, that is the counted analysis the figure comes from. Among them are the afferent coupling of the module, the price of the cut of the module and the test coverage of the area.
* A figure that the counted evidence does not hold is not stated as a value. The area names the missing figure in its reasoning, and the cost of the area rests on the figures that are there.
* The `cost` of an area is a short statement of what the change of the area would cost, resting on the figures that are named beside it.
* The `reasoning` of an area says why the area was read as it was, and why the patterns that fit it fit it.

### The order of the areas

* The areas are ordered by the cost the counted evidence implies, the cheapest area first.
* The order is not a decision. The `reasoning` of the answer states that the order follows the counted evidence and that the choice of the area to work on is the reader's.
* The analysis names no area as the one to start with, and it holds no knowledge of the business value of a change. The figures of the cost are named beside the order, so that a reader who weighs them differently can reorder the list without counting anything again.

### The area types that are absent

* An area type whose input analysis did not run is reported in `absentTypes` with the reason. It is not left out of the result.
* The type `anchor` is absent when the anchor analysis did not run at all.
* The name of an anchor type is absent when that one anchor type could not be reported, because the analysis it rests on did not run while the anchor analysis did run. Use the anchor type name as the `type`, one of `entry-point`, `seam`, `domain`, `build-unit`, `cut-point`, `test-coverage`, `technology-marker`, and name the missing analysis in the reason.
* The type `hotspot` is absent when the hotspot analysis did not run or when it holds no counted hotspot. The areas that rest on the other evidence are still reported in that case.
* The `type` of an absent entry is therefore always one of `anchor`, `hotspot`, or one of the seven anchor type names, so that a reader can tell an absent analysis apart from an absent anchor type inside a running analysis.
* The `absentTypes` list is empty only when every area type is present.

### The limits of the analysis

* The cost of an area rests on the counted evidence of the Java modules alone. A module that is not a Java module, and a class the parser did not see, is not part of the evidence.
* The analysis knows no business value of a change. It ranks by the counted cost only.
* The areas inherit the gaps of the anchor analysis. An anchor type whose input analysis did not run is absent in the anchor result, and the areas of that type are absent with it.
* The patterns come from the catalogue of the marker facts, so a pattern that the catalogue does not hold is never weighed, and the reader does not see a pattern the analysis did not consider.
* No ownership, no age of the code, and no roadmap with dates or owners are part of this analysis.
* The `reasoning` of the answer holds the limits of the analysis next to the reasoning of the areas.

## Hints

* The counted evidence is written as CSV beside the other reports: the inter module dependency matrix, the separation ladder and the test coverage. Use the values the candidate payload reported and name a class and a module as the payload spelled them.
* Report at most 12 areas. A longer response is cut off before its JSON is complete, so it is rejected and retried.
* You must only return clean JSON content without any prefix or postfix.

## Response Requirements

* Use the `ModernisationStrategy` response schema.
* Each `areas[]` entry MUST be a JSON object with the fields `module`, `element`, `anchorType`, `patterns`, `costFigures`, `cost` and `reasoning`. Do NOT use plain strings.
* Each `patterns[]` entry MUST be a JSON object with the fields `name`, `evidenceFor`, `evidenceAgainst` and `whatWouldMakeItWrong`.
* Each `costFigures[]` entry MUST be a JSON object with the fields `name`, `value` and `source`.
* Each `absentTypes[]` entry MUST be a JSON object with the fields `type` and `reason`.
* The `anchorType` of an area is one of `entry-point`, `seam`, `domain`, `build-unit`, `cut-point`, `test-coverage`, `technology-marker`.
* The `name` of a pattern is one of `strangler-fig`, `branch-by-abstraction`, `module-extraction`, `anticorruption-layer`, `horizontal-lift`, `retirement`.
* You must only return clean JSON content without any prefix or postfix.
