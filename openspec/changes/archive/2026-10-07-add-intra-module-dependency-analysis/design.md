# Design

## Context

See proposal.md for motivation. The constraints that shape the approach:

**The parser already makes one visit per class file and throws the useful part away.** `ClassFileParser.getClassModelImports` walks every method's code stream, sees `InvokeInstruction` (owner, name, descriptor) and `FieldInstruction` (owner, name, descriptor), and collapses all of it into a `Set<String>` of qualified type names attached to the `BuildUnit`. Reference counts do not survive. There is no per-method attribution, so cohesion is not derivable.

**The raw module report is the contract between the parser and every Java metric tool.** `java_generate_module_analysis_report` writes `Java/<moduleName>.json`; the metric tools deserialize it into the `Module`/`Package`/`BuildUnit`/`Clazz`/`Method` model. `Batch Java report collection` reuses an existing file without re-parsing. So any model change is simultaneously a report format change and a cache-invalidation problem.

**The analysis state is handed to tools live.** `AnalyseCmd` passes `stateManager.getAnalysisState()` into `AnalysisContext`, which exposes it as `getAnalysisState()`. `JavaTool.getInterModuleDependencyReport` already reads it. The engine saves it at the end of the run through `stateManager.saveState()`. A tool can therefore write results into `analysis.json` directly, with no engine change.

**Handlebars does not escape.** `HandlebarsFactory` registers `EscapingStrategy.NOOP`, so a template prints `-->` verbatim. Diagram source can be emitted by the existing `Documentation.adoc.hbs` without escaping machinery.

Observed scale, measured on the checked-in workspaces: `jabref/jablib` has 148 top-level classes (BuildUnits) but 1592 class objects and 14488 reference entries in a 9.7 MB report; the maven workspace has 87 Java modules and 44 MB of reports.

## Goals / Non-Goals

**Goals:**

- Extend the existing single class-file visit to carry everything the three new analyses need, without a second parse pass.
- Keep every existing Java metric tool behaving exactly as before.
- Make the strength of a dependency a measured, explainable pair of numbers rather than a judgement.
- Produce split proposals that are ranked and priced, never asserted as a verdict.
- Produce diagram source a human can read and the model never has to author.
- Keep the new graph algorithms dependency-free and unit-testable.

**Non-Goals:**

- No change to the Java source parser (`JavaFileParser`) or to the language-level model; this change is about class-file references only.
- No new Maven dependency.
- No rendering binary (Graphviz, PlantUML, kroki). PlantUML is emitted as text; rendering is the reader's toolchain.
- No god-class annotation inside the diagram nodes. The ranking is a separate section; merging the two is deliberately deferred.
- No change to the existing module-level inter-module dependency analysis.
- No cross-module diagram. Each diagram is scoped to one module.

## Decisions

### D1 — Edge weight is two counters per directed pair

For each ordered pair of classes `(A, B)` inside a module, record:

```
   apiWidth   = number of distinct members of B referenced by A
                distinct (owner, name, descriptor) triples
   traffic    = number of reference sites owned by B in A's bytecode
                count of InvokeInstruction + FieldInstruction
```

Alternatives rejected: call-site count alone is inflated by repetition and says nothing about how much of B's interface A actually uses; distinct-member count alone cannot express "how much traffic crosses this seam"; a kind-weighted composite score (`invoke=1, field=5`) invents coefficients that would have to be defended in the document.

The two counters give the diagram a truthful label (`uses 7 of B's methods, 214 sites`) and give the split analysis a weight that is robust under repetition.

### D2 — Edges are classified structural or reference; structural edges are never pruned

```
   STRUCTURAL  extends, implements, declared field type
               a fact about the type system, not a measured coupling
               drawn always, never subject to the diagram cutoff,
               excluded from the split-cost calculation
   REFERENCE   method calls, field accesses, other type uses
               measured, prunable, and the only thing that defines
               "how independent are these two groups"
```

Pruning a structural edge would make the diagram lie about the type hierarchy, and counting inheritance towards separation cost would penalise well-factored hierarchies. The split analysis therefore runs on the reference-edge subgraph, while the diagram draws both.

### D3 — Nodes are BuildUnits, not Clazz objects

`jablib` has 148 BuildUnits but 1592 `Clazz` objects, because nested and inner classes each get a `Clazz`. 1592 nodes cannot be drawn, and the reference record is produced per class file, which is exactly BuildUnit granularity. BuildUnit also matches the existing report shape, so no re-modelling is needed. Nested classes contribute their references to their enclosing BuildUnit.

### D4 — Per-method access counters on `Method`

Four bounded integers per method:

```
   internalFieldAccesses   foreignFieldAccesses
   internalCalls           foreignCalls
```

`internal` means the owner is the enclosing BuildUnit; `foreign` means any other. Four ints per method is bounded (13553 methods in `jablib`); the raw per-method reference lists would not be.

Alternatives rejected: recomputing cohesion in a later pass would require the raw reference lists to be stored, which is exactly the unbounded option; deriving cohesion from the aggregated BuildUnit record is impossible because method-to-field attribution is lost.

### D5 — Namespace filtering happens before any graph analysis

Reports are produced by scanning compiled artefacts, including shaded ones, so foreign classes appear as nodes. This was measured, not assumed: `Apache_Maven.impl` carries five classes of `org.jline.nativ` beside 1129 under `org.apache.maven`; `Apache_Maven.compat` carries `org.fusesource.jansi` and `org.eclipse.sisu.plexus`, the latter including a shaded test class.

The namespace is the deepest package prefix that still covers nine out of ten of the module's classes. The prefix every package shares is useless here, because the only thing `org.apache.maven`, `org.jline.nativ` and `org.fusesource.jansi` have in common is `org`.

Alternatives rejected: `determineProjectNamespace` returns the package of an arbitrary first class, which is a single package and far too narrow for a multi-package module; a two-segment prefix rule over-narrows on a module whose own packages sit at two segments (`demo.core` beside `demo.api`). Measured against the checked-in workspaces, a 90 percent share picks `org.apache.maven`, `org.jabref` and `com.framstag.llmaj` correctly, while 80 percent already cuts off `org.jabref.model` in `jablib`.

Every node and edge is filtered to the namespace *before* connectivity, clustering and cut computation, otherwise clusters are drawn around dependency internals and the result is meaningless. The excluded node and edge counts are reported, not silently dropped; a module whose classes share no sufficient prefix falls back to keeping everything, which is the harmless direction to err in.

### D6 — Split cost is bottleneck cost, ordered by a maximum spanning tree

```
   cost(cut) = min weight over the severed reference edges
```

The whole split hierarchy falls out of Kruskal on a maximum spanning tree in `O(E log E)`: each tree edge in ascending weight order is a candidate separation, and the resulting dendrogram is the ranked ladder. Parameter-free, deterministic, one pass for every level rather than just the best cut.

Reported as a ladder, which is the honest shape:

```
   sever 3 ties (weights 2, 2, 5)          -> 4 groups
   sever 6 ties (weights 2, 2, 5, 9, 11, 19) -> 7 groups
```

Secondary number per split: the total cut weight, from Stoer-Wagner minimum cut, `O(V^3)`, exact and deterministic.

Alternatives rejected: modularity/Louvain needs a resolution parameter and answers "why three groups and not four" only by assertion; weakly connected components is a fact but almost always yields one component on a real module, so it produces nothing actionable; minimum cut alone leads with a recommendation that severs one heavy tie to save several light ones, which is the wrong question when "is this really two modules" depends on whether any strong tie remains.

Splits are explicitly permitted to cut across package boundaries, which is the only way the analysis can say something the package tree does not already say. Package composition is still reported per cluster, as a label, not as a constraint.

### D7 — Graph algorithms implemented in-project behind a small interface

Kruskal maximum spanning tree (~60 lines), Stoer-Wagner minimum cut (~80), connected components (~20), and percentile ranking (~30) are covered by unit tests against small hand-computed graphs. Tarjan SCC already exists in `JavaTool.findCycles`/`strongConnect`. The graph is kept behind an interface so a library can replace it later without touching callers.

Alternative rejected: JGraphT is a general-purpose graph library and would be a legitimate dependency under the project's rules, and it ships Stoer-Wagner. It was rejected because the shaded jar already suffered a packaging incident around class stripping, adding a library surface to obtain one textbook algorithm is a poor trade, and the graph is at most a few hundred nodes.

### D8 — God class detection is a percentile ranking, never a threshold

Each class is ranked within its module on WMC (sum of method cyclomatic complexity), TCC-like cohesion (fraction of method pairs sharing at least one field, derived from the D4 counters), ATFD-like foreign-data access, maximum method nesting, maximum method lines, and afferent plus efferent coupling. The score is the combined rank, and the dominant factor is named.

```
   OrderService   rank 1/214   score 8.4
     WMC 412 ......... 97th percentile
     TCC 0.08 ........  2nd percentile   <- the driver
     ATFD 61 ......... 99th percentile
```

Alternatives rejected: absolute thresholds violate the project rule that the engine assumes imperfect code and reports distance rather than pass/fail; a bare composite score hides why the class ranked, so the reader cannot disagree with the weighting.

Percentiles are relative to the module, so the output never claims a class is bad in absolute terms — only that it stands out from its peers. WMC/TCC/ATFD are the Marinescu detection-strategy factors, so nothing is invented.

Crucially, WMC and cohesion together are what make this a *god class* detector rather than a *big class* detector: a large class whose methods share state scores low on the cohesion factor and is correctly not flagged.

### D9 — Diagram output is PlantUML, deterministic, with no model in its path

`Documentation.adoc` is read through the IntelliJ AsciiDoc plugin, where PlantUML is bundled via the Asciidoctor Diagram extension while Mermaid is experimental. Pandoc renders neither, so the preview toolchain decides the syntax. The choice is isolated in one emitter, so it is a cheap reversal.

The tool writes the diagram source; the model never authors or echoes it. Cluster labels are derived deterministically from cluster composition — the dominant package plus the members that do not fit it:

```
   cluster 3 (14 classes)  mostly org.jabref.logic.bibtex
                            + 3 from org.jabref.model.entry
```

This avoids the ordering problem of naming clusters before the picture is drawn, and removes the failure mode where the model produces invalid diagram syntax or hallucinates edges. The model's evaluation sits in a table next to the picture; it does not touch the picture.

Alternatives rejected: Mermaid (experimental in the target preview); DOT text (not rendered by the target toolchain either); emitting an image file (`image::x.png[]`, which requires a renderer binary and shelling out from the shaded jar); letting the model emit the diagram (token-heavy, corruptible, and every corrupted emission would consume a retry of the task's attempt budget).

### D10 — Two levels per module, cutoff from config, omissions stated

```
   level 1  package overview            always emitted
            nodes = packages, edge weight = aggregated reference weight
   level 2  class detail per cluster    emitted under budget
            nodes = classes in that cluster, edges above minEdgeWeight
```

Configuration: `diagram.maxNodes`, `diagram.minEdgeWeight`, `diagram.maxEdgesPerNode`. Every emitted diagram carries a caption naming what was left out:

```
   18 of 214 classes shown, 61 edges omitted below weight 9
```

Hiding the pruning would contradict the reporting rule this change is built on. A module too large even for level 1 gets a caption saying so rather than a truncated picture.

### D11 — The tool writes results into the analysis state directly

`AnalysisContext.getAnalysisState()` is the live `ObjectNode` that `StateManager` saves at the end of the run. The diagram tools mutate it under a dedicated property; `Documentation.adoc.hbs` prints the stored source verbatim.

Alternatives rejected: letting the model echo the diagram into a schema field costs a full copy of the diagram text per module through the model and stores whatever the model returns, corrupted or not; adding a document-time file include is a new mechanism for something the state node already supports.

Consequence to handle explicitly: a tool call that writes the state and is then followed by a rejected model response leaves the diagram in place while the task step is marked failed. The next run re-executes the step and overwrites the property, so the outcome is idempotent, but the tool must write under a stable key rather than appending.

### D12 — The report format carries a version, and reuse checks it

`Batch Java report collection` reuses an existing `Java/<moduleName>.json` without re-parsing. After this change a reused pre-change report would silently yield unweighted edges and no cohesion. The report gains a format version, and reuse requires a matching version; a mismatch regenerates the report.

### D13 — Existing metric tools keep a derived flat reference list

`getCouplingReport`, `getPackageTangleReport`, `getImportDiversityReport` and `getInterModuleDependencyReport` all consume `BuildUnit.getImports()` as a flat list of qualified type names. The weighted record retains enough information to derive that list without changing its contents, so the four tools are adapted mechanically and their behaviour and outputs are unchanged. This is what keeps the change additive at the report level despite replacing the storage shape.

## Risks / Trade-offs

- Report growth is unmeasured → the per-method counters are bounded at four ints, and the reference record is per-pair rather than per-site; measure report size for `jablib` and a maven module before and after, and if growth is disproportionate, drop `traffic` from storage and recompute it during the class-file visit only.
- Stoer-Wagner is cubic → cap the node count for the secondary number; above the cap, report the ladder from the maximum spanning tree only (which is `O(E log E)`) and say the total-cut figure was skipped.
- PlantUML layout on ~150 nodes is slow and unreadable → the cutoff is load-bearing, not cosmetic; validate the level-1 and level-2 budgets against `jablib` and a maven module before freezing defaults.
- Shaded jars put foreign classes in the graph → namespace filtering before all graph work, with the filtered-out count reported; verify against `jabref`, whose reports suggest heavy shading.
- Cohesion derived from counters is an approximation of TCC → method pairs sharing a field is computed from per-method aggregate counters, not a full method-by-field matrix, so it is a TCC-like signal; document it as such and do not claim TCC.
- PlantUML label escaping → class and package names are dotted identifiers so escaping needs are minimal, but the emitter must still validate that a generated diagram parses, and fall back to a caption if it does not.
- Stale reports silently degrading every new analysis → the D12 version guard, and a visible statement in the task result whenever an unversioned report was regenerated.

## Migration Plan

1. Extend the model and the parser, add the format version, and add the tests. Existing metric tools are adapted in the same step so `mvn verify` stays green.
2. Re-run module analysis for the checked-in workspaces so reports carry the new format.
3. Add the four tools behind their specs, then the two tasks, prompts and schemas.
4. Add the documentation sections and validate the rendered diagrams by eye in the IntelliJ preview.
5. Rollback: the report format version makes a partially migrated workspace detectable; reverting the parser and the model leaves the new properties unread, and `state drop` re-runs only the affected task steps once the code is reverted.

## Calibration

The budgets and the ranking were calibrated against real modules, not guessed. Recorded here because every
one of them is a number a report quotes.

```
   diagram.maxNodes              80      a class detail diagram
   diagram.maxOverviewNodes      250     a package overview, its own budget
   diagram.minEdgeWeight         2
   diagram.maxEdgesPerNode       8
   diagram.maxGroupDiagrams      12
   diagram.minCutNodeLimit       400
   godClass.rankingLimit         15
   godClass.batchRankingLimit     5
```

Measured on the local workspaces:

```
   module                        overview            largest class detail
   ----------------------------  ------------------  ----------------------
   LLMAnalysisJinni              21 packages / 24 e   67 classes, 8 collapsed
   jablib                       140 packages / 475 e   9 classes, 5 collapsed
   maven-core                    55 packages / 89 e   15 classes, 1 collapsed
```

The overview needed a budget of its own. A module of 114 classes can live in 21 packages and still hold one
group of 67 classes, so a single bound tight enough for the class level left the package level nothing to
draw; jablib spans 140 packages. A group of a single class is never drawn, because a one class picture shows
no structure and would push the groups that have structure out of the budget.

Report growth, measured as the share of the compact report the weighted record accounts for: 12 percent on
`jablib` and 17 percent on `maven-core`. That is proportionate, so the `traffic` counter stays stored rather
than being recomputed at read time.

God class ranking on real modules: `JavaTool` (WMC 335, nesting 7, method lines 32, all above the 99th
percentile), `org.apache.maven.project.DefaultProjectBuilder` (WMC 182, 42 foreign data accesses) and
`org.apache.maven.project.MavenProject` (WMC 296, coupling 95) rank first in their modules, which is what a
reader would pick by hand.

The cohesion factor is correct in direction but compressed: 55 percent of `jablib` classes and 38 percent of
`maven-core` classes have no two methods that both touch the state of the class, so a large group ties at
zero and the factor rarely dominates a score. WMC and coupling carry the ranking in practice. A sharper
cohesion measure needs the field identity of each access, which the report does not carry today; that is a
follow-up, and the factor is described as an approximation everywhere it is reported.

## Open Questions

None. The defaults above are recorded in the workspace configuration and can be changed per run without a code
change.
