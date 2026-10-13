# Tasks

## 1. Identifier tokens and lexicon tiers

- [x] 1.1 Add identifier tokenization (camel case boundaries, `_`, digits, lowercase) with dropping of single character tokens and Java keywords; verify with a unit test covering `ProjectBuilder`, `project_builder`, `projectBuilder2`, `newClass`, `a` and `tmpX`
- [x] 1.2 Add the tier model (`technical`, `generic`, `noise`, `unclassified`) and a lexicon reader for a CSV with a version line and `Term,Tier,Note` rows; verify with unit tests for a listed term, an unlisted term becoming `unclassified`, the reported version, and a missing lexicon file failing with a named reason
- [x] 1.3 Add channel accumulation over a module model (`Type`, `Method`, `MethodVerb` for the first token of a method name, `Field`, `Package`, `Doc`) keyed by token; verify with a unit test on a hand-built module asserting the per-channel counts of a known term
- [x] 1.4 Restrict counting to production build units; verify with a unit test whose module holds a production, a test and a generated build unit and which asserts the test and generated names are absent from the evidence

## 2. Per-module vocabulary, metrics and CSV evidence

- [x] 2.1 Compute `distinctDomainTerms`, `technicalShare`, `genericShare` and `topTermShare` from the accumulated counts, excluding the noise tier from the shares; verify with unit tests asserting each share on a hand-built count table, including the division-by-zero case
- [x] 2.2 Compute `purposeCoverage`, `codeCoverage` and `documentationOverlap` from the purpose, architecture and documentation text; verify with unit tests asserting the absent purpose terms, the unavailable state when no description exists, and that an absent description is never reported as zero
- [x] 2.3 Compute `namingCohesion` from the class token sets and the build unit references as the mean overlap of reference-linked class pairs minus the mean overlap of the unlinked pairs, over pairs of different build units; verify with unit tests on a constructed reference graph, including the unavailable state when either side has no pair
- [x] 2.4 Add the insufficient-evidence gate on the configured minimum candidate term count; verify with unit tests for a module below and a module above the bound, asserting the reported state carries the count and no metric values below it
- [x] 2.5 Write the three CSV reports `DomainVocabulary/<module>.csv`, `NamingCohesion/<module>.csv` and `DomainLanguageMetrics/<module>.csv` sorted by term and without timestamps, keeping noise-tier rows and naming the lexicon version; verify with a unit test asserting the file paths, the header, the presence of a noise-tier row and that a second write produces byte-identical content
- [x] 2.6 Add the per-module report provider that reads the module through the existing cached report access, computes the evidence, writes the CSVs and returns the compact rollup; verify with a unit test that runs the provider against a module report file on disk and asserts the rollup without any source or class file of the module being read
- [x] 2.7 Add the extraction step that drops the synthetic names of a class file, reduces a type descriptor to the name it carries, and removes javadoc and HTML markup before the documentation text is tokenized; verify with unit tests for `<init>`, `Ljava/lang/String;`, {@code ...}, `@since` and `<li>`, and with the workspace run whose leading terms changed from `init`/`ljava` to the domain vocabulary

## 3. Batch tool over all modules

- [x] 3.1 Add the batch tool returning `metric`, one rollup entry per Java module and a `skipped` list, reusing the existing all-module report loop; verify with a unit test asserting one entry per module of the fixture state and a named skipped entry for a module without a report
- [x] 3.2 Bound the rollup by the configured leading term count and assert the full term list stays in the CSV; verify with a unit test asserting the returned term list length equals the configured bound while the CSV holds more rows

## 4. Analysis pipeline wiring

- [x] 4.1 Add `analysis/software-architecture/facts/domain_lexicon.csv` with a version line and the seeded tiers of the design table; verify with a unit test that loads it and asserts the tier of every seeded example term
- [x] 4.2 Seed the config properties for the lexicon path, the minimum candidate term count (200) and the leading term count (20) in `Config.seedAnalysisReportBudgets`; verify with a config unit test asserting the seeded values and with a workspace init whose `config.json` holds them
- [x] 4.3 Add the `DomainGlossary` task, its prompt, its response schema and its response property, depending on the module descriptions and the collected Java reports and calling the batch vocabulary tool; verify that the task definition validation passes and that `analyse -o DomainGlossary <workspace>` stores the glossary in `analysis.json` (run on `workspaces/maven`: 154 terms and 20 ambiguous terms stored, 31 s and 104k tokens after the output bound of 6.5)
- [x] 4.4 Add the `DomainVocabularyEvaluationAll` task, its prompt and its `ModuleBatchEvaluation` response, depending on the glossary tag and on the collected Java reports, and requiring findings to quote counted terms and to judge the technical share against the module purpose; verify that the task definition validation passes and that `analyse -o DomainVocabularyEvaluationAll <workspace>` stores findings for all Java modules (run on `workspaces/maven`: 37 module evaluations with 148 findings, quoting the counted shares, 1 m 23 s)
- [x] 4.5 Add the new tasks and files to the analysis README task table and directory listing; verify that every task in `tasks.yaml` has a row and that the listing names the lexicon file and the reference document

## 5. Documentation and reference

- [x] 5.1 Render the domain language chapter in `Documentation.adoc.hbs`: the glossary once, then the per-module findings in the existing evaluation table shape, preceded by the pointer to the reference entry; verify with a template test asserting the chapter heading, the glossary table and the pointer line in rendered output
- [x] 5.2 Add the reference entries for the domain vocabulary and domain language analyses to `AnalysisReference.adoc.hbs`, naming the measured quantities, the absent thresholds, the steering table with lexicon path, minimum term count and leading term count, and the caveats; verify with the reference test asserting the entry count matches the documented set and that the pointer line of the report chapter names an existing entry

## 6. Integration verification

- [x] 6.1 Run `mvn verify` and confirm unit tests, the SBOM check and the jar smoke test pass (468 unit tests, 6 smoke tests, BUILD SUCCESS)
- [x] 6.2 Run the two new tasks against `workspaces/maven`, then rerun them and diff the CSV files to confirm byte-identical evidence, and confirm every module appears either in the result or in the skipped list -- both tasks ran on Maven; the evidence is byte-identical across two runs (`DomainVocabularyWorkspaceTest`), and the tool result accounts for all 90 modules: 37 measured, 50 below the gate with their terms, 3 skipped
- [x] 6.3 Read the leading terms of three modules against their stated purpose and report to the user whether the evidence looks like domain language or like lexicon noise, so the seeded tiers can be adjusted before archive
- [x] 6.4 Tune the two seeded values the corpus argued about: lower the minimum candidate terms to 50 and let a module below it carry its leading terms, so that 67 of 87 Maven modules do not fall out of the judgement unnamed; verify with the unit test that asserts a below-gate module carries its terms and no share, and with the workspace run that judged 13 more module judgements than before
- [x] 6.5 Bound the glossary output in its prompt after the first real run spent 5 m 22 s and 416k tokens on it: the model answered with a 200k character glossary, which was cut off and therefore unparseable on the first two attempts. With the bound of 120 terms the same task succeeds on the first attempt in 31 s and 104k tokens; verify with the two runs whose token and time counts are in the task 4 records above

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result: the new capabilities exist as main specs and the modified ones carry the updated requirements.
