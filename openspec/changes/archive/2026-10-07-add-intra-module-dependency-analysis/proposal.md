# Proposal

## Why

The engine analyses dependencies *between* build modules and *between* packages, but never the reference structure *inside* a module at class level. That gap hides the three questions a reviewer actually asks about a module that has grown too large: which classes are god classes, where would the module naturally break apart, and what does the class graph look like with edge strength.

Two concrete obstacles make this impossible today. `ClassFileParser.getClassModelImports` flattens every reference a class makes into a deduplicated `Set<String>` on the `BuildUnit`, so edge weight is destroyed before any report can see it. And per-method field and call usage is never captured at all, so cohesion cannot be computed and a large cohesive class is indistinguishable from a god class.

The engine also has no diagram output of any kind. Every existing report is a histogram consumed by the model, with no picture for a human reader.

## What Changes

- **BREAKING** (report format): `BuildUnit` gains a weighted reference record replacing the flat `List<String> imports`; `Method` gains internal/foreign field-access and call counters. Existing `Java/*.json` reports do not carry this data and must be regenerated.
- New tool `java_get_class_dependency_graph`: weighted intra-module class reference graph, filtered to the project namespace, with structural and reference edges distinguished.
- New tool `java_get_god_class_ranking`: per-module percentile-ranked class ranking over size, cohesion, foreign-data access, nesting and coupling, with the dominant factor named per class. No thresholds.
- New tool `java_get_split_candidates`: near-independence ladder over the class graph. Primary ordering by bottleneck cost via a maximum spanning tree (single linkage, parameter-free); secondary total-cut weight per split. Splits are explicitly permitted to cut across packages.
- New tool `java_generate_dependency_diagrams`: emits PlantUML source into the analysis state, two levels per module — package overview always, class detail per cluster under a configured node/weight budget, with the omitted counts stated in a caption.
- Two new batch evaluation tasks over all Java modules, following the existing batch-evaluation pattern.
- Two new sections in `Documentation.adoc`, printing the diagram source verbatim alongside the evaluations.
- Report format gains a version marker so stale reports are not silently reused.

## Capabilities

### New Capabilities

- `class-dependency-graph`: weighted, directed, class-level reference graph inside one build module — what a reference is, how its strength is measured, which edges are structural and therefore never pruned, project-namespace filtering, and the report format that carries it.
- `god-class-detection`: per-module ranking of classes by size, cohesion, foreign-data access and coupling, expressed as percentiles within the module with the dominant factor named, never as a pass/fail verdict.
- `split-candidate-analysis`: proposal of module split points by near-independence — a ranked ladder of separations ordered by bottleneck cost with total cut weight reported alongside, allowed to cut across package boundaries.
- `dependency-diagram`: PlantUML rendering of the class graph into the analysis state and the documentation — two levels per module, configured cutoff with an explicit omission caption, deterministic and independent of the model.

### Modified Capabilities

- `batch-java-report-collection`: the "existing raw report reused" requirement must be qualified by report format version, so a report generated before the weighted reference record is regenerated instead of reused by the new analyses.

## Impact

Affected code:

- `tools/java/ClassFileParser.java` — collect per-BuildUnit reference records and per-method access counters in the existing single visit.
- `tools/java/BuildUnit.java`, `Method.java` — new serialized fields.
- `tools/java/JavaTool.java` — four new tools, plus adaptation of the four existing tools that consume `BuildUnit.getImports()` (`getCouplingReport`, `getPackageTangleReport`, `getImportDiversityReport`, `getInterModuleDependencyReport`) so their behaviour is unchanged.
- `analysis/software-architecture/` — task definitions, prompts, response schemas, documentation template sections.
- `config` — diagram budget properties.

Cost and risk:

- Every existing workspace must re-run module analysis; the raw Java reports are the source of truth for all Java metric tools and their format changes.
- Existing report files are large (jablib ≈ 9.7 MB, maven-core ≈ 4.1 MB). The added per-method counters are bounded, but report growth must be measured rather than assumed.
- The largest observed module has ≈148 top-level classes; the secondary total-cut computation is cubic and needs a node cap with a stated fallback.
- Class reports are produced from shaded jars, so foreign classes can appear as nodes. Namespace filtering before clustering is load-bearing, not cosmetic.
- No new Maven dependency: the graph algorithms (Kruskal maximum spanning tree, Stoer-Wagner, connectivity) are implemented in-project behind a small graph interface.
