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

Tasks execute in dependency order, roughly: **project-wide information** → **module discovery** → **per-module analysis** → **batch evaluation of all modules** in one call per metric.

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
| 18 | Collect the raw structural analysis of all Java modules | `CollectJavaModuleReportsAll` | Java, all modules | ✅ Stable |
| 19 | Evaluate cyclomatic complexity | `CyclomaticComplexityEvaluationAll` | Java, all modules | ✅ Stable |
| 20 | Evaluate method visibility distribution | `VisibilityEvaluationAll` | Java, all modules | ✅ Stable |
| 21 | Evaluate class inheritance patterns | `InheritanceEvaluationAll` | Java, all modules | ✅ Stable |
| 22 | Evaluate method complexity (params + LoC) | `MethodComplexityEvaluationAll` | Java, all modules | ✅ Stable |
| 23 | Evaluate method nesting depth | `NestingDepthEvaluationAll` | Java, all modules | ✅ Stable |
| 24 | Evaluate field visibility distribution | `FieldVisibilityEvaluationAll` | Java, all modules | ✅ Stable |
| 25 | Evaluate class cohesion (field count + ratio) | `ClassCohesionEvaluationAll` | Java, all modules | ✅ Stable |
| 26 | Evaluate class coupling (efferent coupling) | `CouplingEvaluationAll` | Java, all modules | ✅ Stable |
| 27 | Evaluate test coverage (naming convention) | `TestCoverageEvaluationAll` | Java, all modules | ✅ Stable |
| 28 | Detect circular dependencies | `CircularDependencyEvaluationAll` | Java, all modules | ✅ Stable |
| 29 | Evaluate method count per class | `MethodCountEvaluationAll` | Java, all modules | ✅ Stable |
| 30 | Evaluate documentation ratio | `DocumentationRatioEvaluationAll` | Java, all modules | ✅ Stable |
| 31 | Detect data class candidates | `DataClassEvaluationAll` | Java, all modules | ✅ Stable |
| 32 | Detect boolean parameter abuse | `BooleanParameterEvaluationAll` | Java, all modules | ✅ Stable |
| 33 | Evaluate annotation usage | `AnnotationEvaluationAll` | Java, all modules | ✅ Stable |
| 34 | Detect package-level tangles | `PackageTangleEvaluationAll` | Java, all modules | ✅ Stable |
| 35 | Evaluate import diversity | `ImportDiversityEvaluationAll` | Java, all modules | ✅ Stable |
| 36 | Evaluate inter-module dependencies | `InterModuleDependencyEvaluation` | General, cross-module | ⚡ Beta |
| 37 | Evaluate the class reference structure and propose module splits; the diagrams it stores are drawn by the reader's preview toolchain, see [Previewing the generated documentation](../../README.md#previewing-the-generated-documentation) | `ClassDependencyGraphEvaluationAll` | Java, all modules | ⚡ Beta |
| 38 | Evaluate god class candidates | `GodClassEvaluationAll` | Java, all modules | ⚡ Beta |
| 39 | Build the ubiquitous language glossary of the project | `DomainGlossary` | Java, cross-module | ⏳ Experimental |
| 40 | Evaluate the domain language of all Java modules | `DomainVocabularyEvaluationAll` | Java, all modules | ⏳ Experimental |
| 41 | Draw the context map of the project from its language | `ContextMapEvaluation` | Java, cross-module | ⏳ Experimental |
### Quality Key

| Rating | Meaning |
|--------|---------|
| ✅ Stable | Well-tested, used in production analysis runs |
| ⚡ Beta | Recently added, basic coverage, may have edge cases |
| ⏳ Experimental | New, limited testing |

## Analysis Methods and Interpretation

`documentation/AnalysisReference.adoc.hbs` states, for every analysis whose numbers a reader cannot read unaided, what it measures, how its result is to be read, what its weights mean and where its limits are. The reference is rendered as a chapter of the generated report, and every section of the report points at its own entry.

This README does not restate a threshold or a metric interpretation. The reference is the single place that states them.

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
│   └── domain_lexicon.csv # The tiers of the vocabulary analysis (technical, generic, noise)
└── documentation/      # Documentation generation template
    ├── AnalysisReference.adoc.hbs # What each analysis measures and how to read it
    └── Documentation.adoc.hbs
```