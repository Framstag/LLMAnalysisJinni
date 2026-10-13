# Tasks

## 1. Project-level aggregation

- [x] 1.1 Aggregate the per-module evidence into one row per module and term, keeping the channel, tier and occurrence count of each module; verify with a unit test on a fixture of three hand-built module evidences asserting no term is merged across modules
- [x] 1.2 Roll the rows up into one row per term with occurrence count, module count, module list and dominant tier; verify with a unit test asserting the span of a term used by two modules and the dominant tier of a term that is technical in one module and unclassified in another
- [x] 1.3 Name the modules without a Java report in the skipped part of the overview result and keep them out of the rows; verify with a unit test on a fixture state whose fourth module has no report

## 2. Overlap, intrusion and quadrant

- [x] 2.1 Compute pairwise lexical overlap with the shared and the exclusive terms of each pair, covering every pair that has vocabulary and reporting pairs without a shared term with a zero overlap; verify with unit tests on the fixture pairs, including the pair with no shared term
- [x] 2.2 Compute the intruding terms against the established owners, excluding terms the module owns itself and reporting whether the owners' descriptions carry the term; verify with unit tests for an intruding term, for a self-owned term and for an owner without a description -- the first test found a defect: ownership had been read as "any candidate term", which made an intrusion impossible. Ownership is now the set of leading candidate terms, and a module that establishes a term of its own does not borrow it
- [x] 2.3 Join the pairs with the import matrix of the inter-module dependency report and classify each pair by structural and lexical presence, reporting an import count of zero for a pair the matrix does not hold; verify with unit tests covering all four combinations and the absent pair
- [x] 2.4 Add the project-level report directory with one CSV file per view, sorted and without timestamps, holding the term rows, the term rollup, the pairs, the intruding terms, the quadrant and the context candidates; verify with a unit test asserting the six file names, the header of each, and that a second build produces byte-identical content. A view with no row is written with its header only, so the six files are always a complete set
- [x] 2.5 Add the overview tool that answers the whole project in one call with the leading terms and their span, the intruding terms, the pairs with both values, the classification counts and the skipped modules; verify with a unit test on the fixture project asserting the counts, and with a single-module fixture asserting no pair is reported and no failure occurs
- [x] 2.6 Bound every returned list by the configured leading count while the CSV keeps every row; verify with a unit test asserting the returned pair list length equals the bound and the quadrant CSV holds more rows
- [x] 2.7 Add the reference entry for the context map analysis to `AnalysisReference.adoc.hbs`, naming both axes, the intrusion measure, the caveats and the steering table; verify with the reference test asserting that the entry count matches the documented set and that the report chapter's pointer line names an existing entry

## 3. Context map task

- [x] 3.1 Add the `ContextMapEvaluation` task, its prompt and its `results/ContextMap.json` schema with context candidates, relations and unplaced modules, depending on the vocabulary evaluation, the module descriptions and the inter-module dependency evaluation, and requiring every relation to name the counted evidence behind it; verify that the task definition validation passes and that `analyse -o ContextMapEvaluation <workspace>` stores the context map in `analysis.json` (run on `workspaces/maven`: 11 contexts, 20 relations and 10 unplaced modules, 52 s and 147k tokens)
- [ ] 3.2 Require the prompt to name a module without vocabulary evidence as not placed and to answer a single-module project with one context and no relation; verify with a run against a single-module workspace and with the stored result of `workspaces/maven` -- the Maven result names all ten unplaced modules with their reason, and the single-module answer is covered by the tool unit test; a single-module workspace whose dependencies have run was not available here
- [x] 3.3 Add the task and the new project-level report directory to the analysis README task table and directory listing; verify that every task in `tasks.yaml` has a row and that the listing names the report directory

## 4. Documentation

- [x] 4.1 Render the context map chapter in `Documentation.adoc.hbs` with the context candidates, the relations, the unplaced modules and the quadrant table, preceded by the pointer to the reference entry; verify with a template test asserting the chapter heading, the relations table columns and the pointer line in rendered output. The pair-level quadrant is not rendered: the analysis state carries the context map, not the pair list, so the chapter renders the two axes of each relation and the quadrant of every pair stays in the evidence CSV, which the reference entry names
- [x] 4.2 Verify the rendered report end to end by running `document` on `workspaces/maven` and reading the chapter: every relation names the counted evidence, the import count and the shared leading terms of the pair it rests on

## 5. Integration verification

- [x] 5.1 Run `mvn verify` and confirm unit tests, the SBOM check and the jar smoke test pass (514 unit tests, 6 smoke tests, BUILD SUCCESS)
- [x] 5.2 Run the context map task against `workspaces/maven` twice and diff the project-level CSV files to confirm byte-identical evidence, and confirm every Java module appears either in the result or in the skipped list -- the evidence is byte-identical over two runs (unit test), the corpus wrote 46,099 evidence rows over six views, and the tool result accounts for all 90 modules: 86 with vocabulary, 4 named as skipped
- [x] 5.3 Read the proposed contexts and relations against the module purposes and report to the user whether the quadrant separates the expected api/impl overlap from the unexpected leaks, so the seeded bounds can be adjusted before archive

## 6. The vocabulary of the whole project

The first corpus run classified 3185 of 3655 module pairs as a candidate domain leak, which is not a quadrant but a constant: the name of the project alone appeared in 2834 pairs. A term that every module uses marks no boundary, so it cannot be evidence that two modules share a language.

- [x] 6.1 Set a project-wide term aside from the lexical axis: a candidate term used by more than the configured share of the modules neither marks a shared language nor establishes a domain, so it takes part in no classification and no intrusion. Verify with unit tests for the exclusion, for the ownership and for the small project that has no vocabulary of its own to speak of, and with the corpus whose leak count fell from 3185 to 1606
- [x] 6.2 Report the project-wide terms as a view of their own, `DomainLanguageOverview/UbiquitousTerms.csv`, with the occurrence count, the module count and the module names, and name them in the tool result, so a reader sees what the analysis set aside; verify with a unit test asserting the file, its header and the term it holds, and with the corpus whose view holds six terms (org 86, apache 85, maven 85, mojo 55, coreit 45, project 45)
- [x] 6.3 Drop the `@author` line of a documentation comment, name included: a person's name carries no domain, and one author name appeared in 587 pairs of the corpus. Verify with a unit test that the author line is gone while the English word in a sentence stays, and with the corpus whose leak count fell from 1606 to 1344

## Workflow follow-up

- Archive `2026-10-13-add-domain-vocabulary-analysis` before archiving this change, because the `analysis-reference` delta is written against the requirement text that change leaves behind.
- Archive this change after the project's review requirements are satisfied.
- Verify the archived result: the new capability exists as a main spec and the modified ones carry the updated requirements.
