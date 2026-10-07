# Tasks

## 1. Weighted reference model and parser

- [x] 1.1 Extend the raw module report model with the weighted reference record, the four per-method access counters and a report format version; verify with a unit test that all three survive a Jackson write-and-read round trip
- [x] 1.2 Collect the counters during the existing class-file visit: distinct referenced members and reference sites per referenced class, plus internal and foreign field accesses and calls per method; verify with a unit test over a fixture class that calls one method repeatedly, calls three distinct methods, reads an own field and reads a foreign field
- [x] 1.3 Classify superclass, implemented interface and declared-field-type references as structural and exclude them from the reference counters; verify with a unit test that an inheritance-only relation yields a structural edge and no reference edge
- [x] 1.4 Record the report format version in every generated report; verify the written file carries it and that loading a report exposes it

## 2. Existing metric tools keep their behaviour

- [x] 2.1 Keep the flat referenced-type list beside the weighted record; verify with a golden-output test that the coupling, package tangle, import diversity and inter-module dependency reports are identical for a fixture module with and without the weighted record, which is the pre-change format
- [x] 2.2 Regenerate one workspace report and confirm by eye that those four reports are unchanged against the pre-change run

## 3. Report reuse guard

- [x] 3.1 Make batch report collection reuse a report only when its format version matches, regenerating and naming the reason otherwise; verify with a unit test using an older-format fixture report that the descriptor states the regeneration and the reason
- [x] 3.2 Verify end to end by placing an older-format report in a scratch workspace, running the batch collection and observing that the report is regenerated and the reason is reported

## 4. Graph algorithms

- [x] 4.1 Implement connected components, maximum spanning forest and the single-linkage separation ladder behind a small graph interface; verify against a hand-computed four-node graph with one heavy and three light edges
- [x] 4.2 Implement the exact minimum cut; verify against hand-computed graphs including a disconnected graph and one with two equal-cost cuts
- [x] 4.3 Implement percentile ranking within a value set; verify tie handling and a single-element set in unit tests

## 5. Class dependency graph tool

- [x] 5.1 Implement the class dependency graph tool returning nodes, weighted edges and the structural and reference edge counts for a module; verify with a unit test over a fixture report
- [x] 5.2 Filter nodes and edges to the project namespace before any graph work and report the excluded node and edge counts; verify with a fixture report containing shaded foreign classes that they appear neither as nodes nor as targets and that the counts are reported
- [x] 5.3 Return a regeneration notice instead of an empty graph when the report carries no reference records; verify with an older-format fixture report

## 6. God class ranking tool

- [x] 6.1 Implement the god class ranking tool over all six factors; verify with a fixture containing a large cohesive class and a large incohesive class that they rank differently and that the incohesive one scores higher
- [x] 6.2 Report the dominant factor, the per-factor percentiles, the rank, the total class count and the entry limit; verify each field is present in the tool test
- [x] 6.3 Confirm no threshold or boolean verdict is produced; verify the tool test asserts the result contains only percentiles, ranks and grades
- [x] 6.4 Describe the cohesion factor as a TCC-like approximation in the tool description; verify the text appears in the tool's registration

## 7. Split candidate tool

- [x] 7.1 Implement the split candidate tool returning the bottleneck-ordered ladder from the maximum spanning forest; verify with a fixture where a single heavy edge dominates the cost ranking
- [x] 7.2 Add the secondary total cut weight with a configured node cap and a not-computed marker above it; verify with a fixture above the cap that the ladder is still returned and the marker names the limit
- [x] 7.3 Report each group's size, dominant package and non-conforming member count, and allow groups that cross packages; verify with a fixture whose cheapest separation cuts across packages
- [x] 7.4 Confirm the ladder is parameter-free and repeatable; verify that two calls on the same fixture produce identical results and that the tool accepts no group count or seed

## 8. Dependency diagram emitter

- [x] 8.1 Implement the diagram tool writing PlantUML source into the analysis state under a module-specific key; verify by calling it twice and asserting the analysis state holds exactly one diagram set for that module
- [x] 8.2 Emit the package-level overview by aggregating class-level edge weights per package pair; verify against a fixture module with a known aggregation
- [x] 8.3 Emit class-level diagrams within the configured node and edge-weight budget, with a caption naming shown and total class counts and the omitted edge count; verify the caption contents and that a group above the node budget is reported as not drawn
- [x] 8.4 Draw structural edges regardless of the cutoff; verify with a fixture where a class's only in-budget edge is inheritance that the inheritance edge is present
- [x] 8.5 Validate generated source before storing it and store an explanatory caption instead of invalid source; verify with an injected invalid label that no diagram is stored and the failure is reported

## 9. Pipeline tasks, prompts, schemas and configuration

- [x] 9.1 Add the class dependency graph evaluation task with prompt and response schema, calling the graph and split tools; verify the task definition validation test passes and `state dump` lists the task
- [x] 9.2 Add the god class evaluation task with prompt and response schema; verify the task definition validation test passes and the batch grouping rule is stated in the prompt
- [x] 9.3 Have the dependency task prompt call the diagram tool; verify by running the task against a small workspace and observing the diagram property in `analysis.json`
- [x] 9.4 Add the diagram budget properties to the workspace configuration with defaults and validation; verify the config validation test passes and the properties appear in a freshly initialised workspace
- [x] 9.5 Update the task table in `analysis/software-architecture/README.md`; verify the README task-table test passes

## 10. Documentation sections

- [x] 10.1 Add the dependency diagram and split candidate sections to `Documentation.adoc.hbs`, printing the stored source verbatim; verify the template test asserts arrow syntax survives rendering
- [x] 10.2 Add the god class ranking section; verify the template test asserts the section renders when the ranking is present and prints a reason when it is absent
- [x] 10.3 Render `Documentation.adoc` for a workspace and confirm in the AsciiDoc preview that both diagrams render and the omission captions are legible

## 11. Integration, measurement and calibration

- [x] 11.1 Regenerate the module reports of the checked-in workspaces and verify every report records the current format version
- [x] 11.2 Measure raw report size before and after for `jablib` and one maven module and record the growth; verify the growth is proportionate and that the measurement is written down in the change notes
- [x] 11.3 Calibrate the diagram budgets and the god class factor weights against `jablib` and one maven module; verify the rendered diagrams are legible and record the chosen defaults
- [x] 11.4 Run `mvn verify` and confirm the full test suite including the artefact smoke test passes

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived specs contain the four new capabilities and the modified batch report collection delta.
