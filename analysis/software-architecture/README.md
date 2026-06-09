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

### Quality Key

| Rating | Meaning |
|--------|---------|
| ✅ Stable | Well-tested, used in production analysis runs |
| ⚡ Beta | Recently added, basic coverage, may have edge cases |
| ⏳ Experimental | New, limited testing |

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
    └── Documentation.md.hbs
```