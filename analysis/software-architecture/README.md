# Software Architecture Analysis

## Purpose

This directory contains the analysis pipeline definition for the **Software Architecture** domain. The pipeline performs automated code architecture analysis through structured, iterative LLM calls — chaining Handlebars-prompted tasks that each evaluate a specific aspect of the codebase.

## How It Works

Each task in `tasks.yaml` defines:
- A **prompt** (Handlebars template enriched with dynamic context)
- A **JSON Schema** response format (enforces structured output)
- A **dependency graph** (tasks wait on tags from other tasks)
- Optional **loop** support (iterate over modules)
- A **tool whitelist** (which MCP tools the LLM can call)

Tasks execute in dependency order, roughly: **project-wide information** → **module discovery** → **per-module analysis** → **per-module evaluation**.

## Task Table

| # | Goal | Task ID | Scope | Quality |
|---|------|---------|-------|--------|
| 1 | Welcome and orient the LLM | `Welcome` | General, project-wide | ✅ Stable |
| 2 | Locate project README file | `LocateREADME` | General, project-wide | ✅ Stable |
| 3 | Summarize project metadata | `ProjectSummary` | General, project-wide | ✅ Stable |
| 4 | Detect used build systems | `BuildSystems` | General, project-wide | ✅ Stable |
| 5 | Locate code modules | `LocateModules` | General, project-wide | ✅ Stable |
| 6 | Detect programming languages per module | `ProgrammingLanguages` | General, per-module | ✅ Stable |
| 7 | Analyze build files per module | `ModuleBuildfileAnalysis` | General, per-module | ✅ Stable |
| 8 | Determine module purpose | `ModulePurpose` | General, per-module | ✅ Stable |
| 9 | Determine module architecture pattern | `ModuleArchitecture` | General, per-module | ✅ Stable |
| 10 | Locate SBOM file | `SBOMLocation` | General, project-wide | ✅ Stable |
| 11 | Load SBOM contents | `LoadSBOM` | General, project-wide | ✅ Stable |
| 12 | Extract dependency list from SBOM | `DependencyList` | General, project-wide | ✅ Stable |
| 13 | Describe technology stack from dependencies | `TechnologyStack` | General, project-wide | ✅ Stable |
| 14 | Evaluate license compliance | `LicenseEvaluation` | General, project-wide | ✅ Stable |
| 15 | Collect file type statistics | `FileStatistics` | General, per-module | ✅ Stable |
| 16 | Evaluate code size distribution | `CodeSizeDistribution` | General, project-wide | ✅ Stable |
| 17 | Identify directory structure per module | `ModuleSubdirectories` | General, per-module | ✅ Stable |
| 18 | Collect raw structural analysis per module | `ModuleAnalysisReports` | Java, per-module | ✅ Stable |
| 19 | Evaluate cyclomatic complexity | `ModuleCyclomaticComplexityEvaluation` | Java, per-module | ✅ Stable |
| 20 | Evaluate method visibility distribution | `ModuleVisibilityEvaluation` | Java, per-module | ⚡ Beta |
| 21 | Evaluate class inheritance patterns | `ModuleInheritanceEvaluation` | Java, per-module | ⚡ Beta |
| 22 | Evaluate method complexity (params + LoC) | `ModuleMethodComplexityEvaluation` | Java, per-module | ⚡ Beta |
| 23 | Evaluate method nesting depth | `ModuleNestingDepthEvaluation` | Java, per-module | ⚡ Beta |
| 24 | Evaluate field visibility distribution | `ModuleFieldVisibilityEvaluation` | Java, per-module | ⚡ Beta |
| 25 | Evaluate class cohesion (field count + ratio) | `ModuleClassCohesionEvaluation` | Java, per-module | ⚡ Beta |
| 26 | Evaluate class coupling (efferent coupling) | `ModuleCouplingEvaluation` | Java, per-module | ⚡ Beta |
| 27 | Evaluate test coverage (naming convention) | `ModuleTestCoverageEvaluation` | Java, per-module | ⚡ Beta |
| 28 | Detect circular dependencies | `ModuleCircularDependencyEvaluation` | Java, per-module | ⚡ Beta |
| 29 | Evaluate method count per class | `ModuleMethodCountEvaluation` | Java, per-module | ⚡ Beta |
| 30 | Evaluate documentation ratio | `ModuleDocumentationRatioEvaluation` | Java, per-module | ⚡ Beta |
| 31 | Detect data class candidates | `ModuleDataClassEvaluation` | Java, per-module | ⚡ Beta |
| 32 | Detect boolean parameter abuse | `ModuleBooleanParameterEvaluation` | Java, per-module | ⚡ Beta |
| 33 | Evaluate annotation usage | `ModuleAnnotationEvaluation` | Java, per-module | ⚡ beta |
| 34 | Detect package-level tangles | `ModulePackageTangleEvaluation` | Java, per-module | ⚡ beta |
| 35 | Evaluate import diversity | `ModuleImportDiversityEvaluation` | Java, per-module | ⚡ beta |
| 36 | Evaluate inter-module dependencies | `InterModuleDependencyEvaluation` | General, cross-module | ⚡ beta |
| 37 | Evaluate the class reference structure and propose module splits; the diagrams it stores are drawn by the reader's preview toolchain, see [Previewing the generated documentation](../../README.md#previewing-the-generated-documentation) | `ClassDependencyGraphEvaluationAll` | Java, all modules | ⚡ Experimental |
| 38 | Evaluate god class candidates | `GodClassEvaluationAll` | Java, all modules | ⚡ Experimental |

### Quality Key

| Rating | Meaning |
|--------|---------|
| ✅ Stable | Well-tested, used in production analysis runs |
| ⚡ Beta | Recently added, basic coverage, may have edge cases |
| ⏳ Experimental | New, limited testing |

## Dependency Diagrams

`ClassDependencyGraphEvaluationAll` builds the class reference graph of the Java modules and stores PlantUML source for it. The engine does not draw the diagrams and writes no image file; the preview toolchain of the reader draws them, see [Previewing the generated documentation](../../README.md#previewing-the-generated-documentation).

The source is derived from the parsed reports, so it is identical on every run and carries no model-generated content. The evaluation text next to a picture does not touch the picture: the model interprets the structure, it never authors or alters the diagram.

### Two levels per module

| Level | Nodes | Emitted |
|-------|-------|---------|
| Package overview | one per package | whenever the module has production classes and stays within `diagram.maxOverviewNodes` |
| Class detail | the classes of one group | once per group that holds at least two classes and stays within `diagram.maxNodes` |

The package overview draws one node per package and one edge between two packages whose weight is the aggregated weight of the class references between them.

The class detail diagrams show the groups of the cheapest separation of the class reference graph, which is the split at the lowest total coupling. A module whose production classes are already separate has no such separation, and its packages are drawn as the groups instead. A group of a single class is skipped, because a diagram of one class shows no structure and would only use up the diagram budget.

A class detail diagram draws its own group as classes and every other group as one collapsed rectangle, weighted with the total coupling to that group. At most the eight heaviest of those collapsed groups are drawn, so a group coupled to many others does not push its own classes out of the picture. Every inheritance or implemented interface relation is drawn whenever both of its endpoints are in the diagram, whatever the weight cutoff says.

Group labels are derived from group composition, not from the model: the dominant package of the group plus the count of members that do not belong to it.

### Omissions are stated in the caption

Nothing is dropped silently:

- The caption of a diagram names how many classes it shows out of the total, how many edges were omitted below `diagram.minEdgeWeight`, how many structural relations it draws regardless of the cutoff, and how many other groups it shows collapsed.
- A group above the node budget, and a group that arrives after the detail diagram budget is used up, is listed under "Not drawn" with the budget it exceeded.
- A module above the package budget gets no overview, and a module whose class name cannot be written into the PlantUML source gets no diagram at all. Both cases are reported with their reason instead of leaving a hole in the document.

### Configuration

The budgets are seeded into the `config.json` of every workspace, so a run can be reproduced from the configuration it was produced with:

| Property | Default | Bounds |
|----------|---------|--------|
| `diagram.maxNodes` | 80 | classes in one class detail diagram |
| `diagram.maxOverviewNodes` | 250 | packages in the package overview |
| `diagram.minEdgeWeight` | 2 | reference weight below which a reference is not drawn |
| `diagram.maxEdgesPerNode` | 8 | edges drawn per class; the edges dropped this way count as omitted |
| `diagram.maxGroupDiagrams` | 12 | class detail diagrams emitted per module |
| `diagram.minCutNodeLimit` | 400 | class count above which the exact minimum-cut figure of a separation is skipped; the ladder the diagrams' groups come from is derived without it |

The behaviour behind these numbers is specified in `dependency-diagram`.

## God Class Ranking

`GodClassEvaluationAll` ranks the production classes of every Java module by how far each stands out from its peers, and interprets the ranking per module.

### What the ranking does and does not say

Every factor is a percentile within the module. No absolute threshold decides the outcome and no class is classified as a god class - a rank of 1 of 214 says that the class stands out among its peers in that module, not that it is bad. Because the percentiles are relative, the same class ranked in a smaller or simpler module gets different values. The result names the factor that drove a class's score, so the weighting can be disputed instead of trusted.

### Factors

| Factor | Meaning |
|--------|---------|
| `WMC` | sum of the cyclomatic complexity of the class's methods |
| `cohesion (TCC-like)` | share of method pairs that both work on the state of the class - an approximation, not the exact metric |
| `foreign data accesses` | accesses to fields declared outside the class |
| `maximum nesting depth` | greatest nesting depth among the class's methods |
| `maximum method lines` | longest method of the class |
| `module coupling` | afferent and efferent coupling of the class within the module |

The score is the mean of the factor percentiles that count as badness. Cohesion is the one factor where a low value is the problem, so it is read inverted. Size and cohesion together are what make this a god class ranking rather than a large class ranking: a large class whose methods work on its own state scores high on cohesion and is correctly not flagged, while a class whose methods each touch their own corner of the state loses that protection.

### Configuration

| Property | Default | Bounds |
|----------|---------|--------|
| `godClass.rankingLimit` | 15 | ranked classes reported for one module |
| `godClass.batchRankingLimit` | 5 | ranked classes reported per module when all modules are ranked in one call, which is what the batch task does |

The factors and the percentile rule are specified in `god-class-detection`.

## Projects Without an SBOM

The analysis runs on projects that do not ship a Software Bill of Materials. `SBOMLocation` records
that absence (`sbom.found` is `false`, `sbom.path` is empty) and stays successful; it is a result of
the analysis, not a defect of the run.

* `LoadSBOM` answers with the result `ERROR` and a reason naming the absence instead of claiming
  dependency data. A tool call without a path answers that the input is missing.
* The tasks that use dependency data - `DependencyList`, `TechnologyStack`, `LicenseEvaluation` -
  complete with an explicitly empty result and a reason that names the missing dependency data, so the
  document shows why a section is empty instead of showing a failure.
* A task is never rejected because dependency data is missing. A rejection is always a response that
  does not conform to its schema, which is what the run reports and retries.

## Adding New Tasks

1. Add the task definition to `tasks.yaml` with id, prompt, schema, dependencies, tags, and tool whitelist
2. Add a new row to the table above (order: roughly execution order)
3. Adjust quality rating based on testing maturity

## Directory Structure

```
analysis/software-architecture/
├── tasks.yaml          # Task definitions with dependency graph
├── README.md           # This file
├── prompts/            # Handlebars prompt templates
│   ├── systemprompt.md # Shared system prompt (JSON enforcement)
│   ├── welcome.md
│   └── ...
├── results/            # JSON Schema files for LLM response validation
│   ├── ArchitectureEvaluation.json
│   └── ...
├── macros/             # Shared Handlebars partials
│   ├── list_of_modules.md
│   ├── current_loop_module.md
│   └── ...
├── facts/              # Static knowledge (e.g., build system wildcards)
└── documentation/      # Documentation generation template
    └── Documentation.adoc.hbs
```