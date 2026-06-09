## Why

The current system analyzes each module in isolation — per-class Ce (efferent coupling), cyclomatic complexity, inheritance depth, etc. But no tool measures **how modules relate to each other**. Without inter-module dependency data, we cannot detect:

- Architecture violations (foundation modules depending on application modules)
- Hub modules with excessive fan-in (single points of failure)
- Unstable modules that should be refactored
- Orphan modules disconnected from the system

Adding afferent coupling (Ca), inter-module fan-out (Ce-inter), and Instability (Ce/(Ce+Ca)) closes a critical gap between class-level metrics and system-level architecture understanding.

## What Changes

- **New Java tool**: `java_get_inter_module_dependency_report` that reads all module reports, builds a namespace index from class qualified names, classifies imports as inter-module vs third-party, and produces per-module Ca, Ce-inter, and Instability metrics
- **New evaluation task**: `InterModuleDependencyEvaluation` that calls the tool and produces architecture findings with recommendations
- **New spec**: `inter-module-dependency-analysis` defining the capability
- **Updated documentation**: `Documentation.md.hbs` gains a cross-module dependencies section
- **Updated task table**: README.md reflects the new task

## Capabilities

### New Capabilities

- `inter-module-dependency-analysis`: Tool and evaluation for computing inter-module dependencies (fan-in Ca, fan-out Ce-inter, Instability metric), dependency matrix export, and architecture violation detection

### Modified Capabilities

- *(none — existing spec requirements unchanged)*

## Impact

- **New Java code**: `JavaTool.java` — 1 public tool method + 2 private helpers (~237 lines)
- **New prompt**: `analysis/software-architecture/prompts/inter_module_dependency_evaluation.md`
- **New task**: `analysis/software-architecture/tasks.yaml` — one new entry
- **New spec**: `openspec/specs/inter-module-dependency-analysis/spec.md`
- **Doc update**: `analysis/software-architecture/documentation/Documentation.md.hbs`
- **README update**: `analysis/software-architecture/README.md`