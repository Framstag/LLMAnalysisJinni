# Tasks

## 1. Reference document skeleton

- [x] 1.1 Create `analysis/software-architecture/documentation/AnalysisReference.adoc.hbs` with the chapter heading, the entry pattern holding the parts *What is measured*, *How to read it*, *Weights* and *Caveats* plus a steering table with the columns *Value*, *Bounds* and *Source*, and the god class entry as the worked example. Verify by reading the file as AsciiDoc: one chapter heading, one entry heading, the four parts and the table are present.
- [x] 1.2 Add `src/test/java/com/framstag/llmaj/documentation/AnalysisReferenceTest.java` that parses the reference file and asserts, for every entry, that the four parts and a steering table with a `Source` column are present. Verify with `mvn -q test -Dtest=AnalysisReferenceTest`.
- [x] 1.3 Name the new test file in the recursive listing fixture of `FileToolTest`, which pins the contents of the test tree and therefore fails on any added test file. Verify with `mvn -q test -Dtest=FileToolTest`.

## 2. Entries for the java metric evaluations

- [x] 2.1 Write the entries for cyclomatic complexity, method complexity, nesting depth and method count, each stating the thresholds the per-module prompt files apply today (`CC > 10`, `> 7` parameters, `> 30` statements, nesting `> 4`, `> 20` methods). Verify `AnalysisReferenceTest` passes and that each threshold appears in the prompt file it came from.
- [x] 2.2 Write the entries for method visibility, field visibility, inheritance and annotation usage (more than 50 percent public methods, more than 40 percent static methods, more than 5 implemented interfaces, inheritance depth greater than 3, more than 60 percent of annotations from one framework). Verify `AnalysisReferenceTest` passes and that each threshold appears in the prompt file it came from.
- [x] 2.3 Write the entries for class cohesion, coupling, circular dependencies, package tangles and import diversity (field count above 10 with more than 20 methods, more than 50 percent of imports from one external module, cycles over 3 classes, cycles over 3 packages, more than 80 percent external imports, more than 40 percent `java.*` imports). Verify `AnalysisReferenceTest` passes and that each threshold appears in the prompt file it came from.
- [x] 2.4 Write the entries for test coverage, documentation ratio, data class candidates and boolean parameters (test-to-production ratio below 50 percent, less than 50 percent documented classes, 3 or more boolean parameters). Verify `AnalysisReferenceTest` passes and that each threshold appears in the prompt file it came from.

## 3. Entries for the module and graph level analyses

- [x] 3.1 Write the inter-module dependency entry: Ca, Ce and Instability as module counts, the 30 and 70 instability bands, the direction rule, and the CSV matrix import count as the weight. Verify `AnalysisReferenceTest` passes and that the bands match `openspec/specs/inter-module-dependency-analysis/spec.md`.
- [x] 3.2 Write the class reference structure entry: the reference graph, the edge weight as the number of references between two classes, the separation cost as the strongest severed tie, and the split candidate price. Verify `AnalysisReferenceTest` passes and that the cost definition matches `openspec/specs/class-dependency-graph/spec.md`.
- [x] 3.3 Write the dependency diagrams entry: both levels, the group derivation from the cheapest separation, the collapsed groups, the caption omissions and the undrawn-diagram reporting, with the six `diagram.*` properties and their seeded values in the steering table. Verify `AnalysisReferenceTest` passes and that a test comparing the seeded values named there with the diagram defaults of the configuration fails when one is changed.
- [x] 3.4 Write the god class entry: the six factors, the percentile-within-module rule, the inverted cohesion factor, the cohesion approximation and the two ranking limits, with `godClass.*` in the steering table. Verify `AnalysisReferenceTest` passes and that a test comparing the named limits with the god class defaults of the configuration fails when one is changed.
- [x] 3.5 Extend the expected set of `AnalysisReferenceTest` to all 21 documented analyses and verify that `mvn -q test -Dtest=AnalysisReferenceTest` passes.

## 4. Threshold migration and removal of the unreferenced prompts

- [x] 4.1 Compare the 18 `analysis/software-architecture/prompts/module_*_evaluation.md` files against the reference entries and verify that every threshold and interpretation rule they state appears in an entry, with none left only in a prompt.
- [x] 4.2 Delete the 18 unreferenced prompt files and add a test that every file in the prompt directory is named by a task in `tasks.yaml`. Verify with `mvn -q test` and by grepping `tasks.yaml` and `openspec/specs/` for the deleted names, which must return nothing outside archived changes.
  - Decision taken during apply, at the user's direction: the 17 `module_*_evaluation.md` files are deleted and the one-time grep is the verification; the planned test is skipped, so a future dead prompt would go unnoticed. `module_analysis_reports.md` stays: it is not an evaluation prompt, carries no interpretation rule, and `SoftwareArchitectureTaskConfigTest` pins its wording.

## 5. Report integration

- [x] 5.1 Include the reference as the report's last chapter in `analysis/software-architecture/documentation/Documentation.adoc.hbs` and extend `DocumentationTemplateTest` to assert the rendered chapter text equals the reference file text. Verify with `mvn -q test -Dtest=DocumentationTemplateTest`.
- [x] 5.2 Add a pointer line naming its reference entry to each of the 21 documented sections, and replace the explanatory paragraph of the *Dependency Diagrams per Module* section with its pointer line so the diagram facts are stated only in the entry. Extend `DocumentationTemplateTest` to assert that the 21 sections carry a pointer line and that every pointer names an existing entry. Verify with `mvn -q test -Dtest=DocumentationTemplateTest`.
- [x] 5.3 Verify `mvn verify` passes with the chapter, the pointer lines and the reference in place.

## 6. Analysis README

- [x] 6.1 Replace the README sections *Dependency Diagrams* and *God Class Ranking* with a pointer section naming the reference as the place that describes what an analysis measures and how to read it. Verify no threshold or metric interpretation remains in the README and that the reference entries carry the removed statements.
- [x] 6.2 Add `AnalysisReference.adoc.hbs` to the README directory listing and extend the existing README test to assert that the listing names the reference document and that the README holds no threshold. Verify with `mvn -q test`.

## 7. Integration verification

- [x] 7.1 Generate the document for two existing workspaces and verify that both reference chapters are identical, that every pointer line names an entry the chapter holds, and that a section for an analysis without a result still leaves the reader the chapter.
- [x] 7.2 Verify `mvn verify` passes on a clean checkout.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
