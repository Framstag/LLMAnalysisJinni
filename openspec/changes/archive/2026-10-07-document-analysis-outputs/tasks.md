# Tasks

## 1. Dependency diagram section in the analysis README

- [x] 1.1 Add a `### Dependency Diagrams` section to `analysis/software-architecture/README.md` after the task table, stating that each module gets a package-level overview that aggregates the couplings between its packages and, in addition, class detail diagrams for its groups, and that the groups are the cheapest separation of the module's class reference graph. Verify: both levels are described and the group derivation is stated, matching the `dependency-diagram` spec.
- [x] 1.2 State in that section that a class detail diagram draws the other groups collapsed and weighted with the total coupling to them, that every diagram caption names how many classes it shows out of the total and how many edges fell below the minimum weight, and that a group above the node budget or a class name that cannot be written into the diagram syntax is reported with its reason rather than drawn or stored broken. Verify: a reader can tell from the section why a picture shows fewer nodes than the module has classes and why a diagram may be missing entirely.
- [x] 1.3 Add a table of the diagram configuration properties to that section - `diagram.maxNodes`, `diagram.maxOverviewNodes`, `diagram.minEdgeWeight`, `diagram.maxEdgesPerNode`, `diagram.maxGroupDiagrams`, `diagram.minCutNodeLimit` - with the value `Config.seedAnalysisReportBudgets()` seeds and what each bounds. Verify: every property name and value matches `Config`, and the description of each names what it bounds rather than repeating the property name.
- [x] 1.4 Link the section to the preview guidance in the root `README.md` instead of repeating it. Verify: the link resolves to the preview subsection and the section contains no second copy of the toolchain table.

## 2. God class section in the analysis README

- [x] 2.1 Add a `### God Class Ranking` section after the diagram section, opening with what the ranking does not claim: every factor is a percentile position within the module, no absolute threshold decides the outcome, and no pass or fail verdict is produced. Verify: the no-threshold and no-verdict statements precede the factor list.
- [x] 2.2 Name the scored factors in that section - size as the summed cyclomatic complexity of the methods, cohesion, foreign data access, nesting, maximum method lines, module coupling - describe cohesion as an approximation rather than the exact metric, and state that a large class whose methods work on its own state is not a candidate because its cohesion percentile is high. Verify: the factor names match `GodClassRanking` and the cohesion wording overclaims nothing.
- [x] 2.3 Add a table of `godClass.rankingLimit` and `godClass.batchRankingLimit` to that section with the seeded defaults and what each bounds. Verify: both property names and values match `Config`, and a reader can tell that the batch limit is the one that applies when all modules are reported at once.
- [x] 2.4 Cross-read both new sections against the specs `dependency-diagram` and `god-class-detection` and against `GodClassRanking`, `DependencyDiagrams` and `Config`, and correct anything that does not match. Verify: no statement in either section contradicts the code or the specs, and the sections link to those specs instead of restating their requirements.

## 3. Integration checks

- [x] 3.1 Run `mvn verify` and confirm it still passes, so the docs-only change is shown not to touch the build or the tests. Verify: the build is green.
- [x] 3.2 Run `openspec validate "document-analysis-outputs" --strict` and confirm the change validates. Verify: no validation warnings or errors.

## 4. Corrections to the existing analysis README content

- [x] 4.1 Remove the blank lines that separate rows 33 to 37 of the task table in `analysis/software-architecture/README.md`, so the table is one contiguous table. Verify: no blank line stands between two task rows, and the rendered table has one header row.
- [x] 4.2 Correct the directory structure section of that README to name the documentation template as it is, `Documentation.adoc.hbs`, and check every other file the section names by name against the directory. Verify: each named file exists in `analysis/software-architecture/`.
- [x] 4.3 Re-run the integration checks of group 3 after these corrections - `mvn verify` and `openspec validate "document-analysis-outputs" --strict`. Verify: the build is green and the change validates.

## Workflow follow-up

- Archive the change once the documentation edits are merged.
