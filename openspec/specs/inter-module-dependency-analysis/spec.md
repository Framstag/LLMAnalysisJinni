# Inter-Module Dependency Analysis

## Purpose

Provides tools and evaluation tasks for computing inter-module dependencies across all build modules in a project. Produces per-module afferent coupling (Ca), inter-module efferent coupling (Ce-inter), Instability metric, and a full dependency matrix. Enables detection of architecture violations, hub modules, orphan modules, and instability imbalances.

## Requirements

### Requirement: Inter-module dependency report tool

The system SHALL provide a tool that reads all module analysis reports, builds a namespace-to-module mapping from class qualified names, classifies each import across all modules as either intra-module, inter-module (project), or third-party, and produces:
- Inter-module Ce per module: number of unique other project modules a module depends on
- Afferent coupling (Ca) per module: number of unique project modules that depend on this module
- Instability per module: Ce / (Ce + Ca), scaled to 0-100
- Full dependency matrix as a CSV file

The tool SHALL use longest-prefix matching of package namespaces to correctly map imports to owning modules where namespace overlap exists.

The tool SHALL filter out JDK standard imports (`java.`, `javax.`, `java.lang`) and static imports.

The tool SHALL handle wildcard imports (ending in `.*`) by stripping the wildcard and matching the package against known module packages.

The tool SHALL return early with empty results if fewer than 2 modules have analysis reports available.

#### Scenario: Successful inter-module dependency report generation
- **WHEN** the LLM calls `java_get_inter_module_dependency_report`
- **THEN** the tool returns 3 distributions: Ce-inter per module, Ca per module, Instability per module

#### Scenario: CSV dependency matrix written
- **WHEN** the LLM calls `java_get_inter_module_dependency_report`
- **THEN** the tool writes `InterModuleDependencyMatrix.csv` with columns From, To, ImportCount

#### Scenario: Single module project
- **WHEN** the project has only 1 module
- **THEN** the tool returns an empty list (no inter-module analysis needed)

#### Scenario: Module with no Java report
- **WHEN** a module has no JavaParser report available
- **THEN** the tool logs a warning and skips that module

#### Scenario: Import matches exact class in another module
- **WHEN** an import string exactly matches a class's qualifiedName in another module
- **THEN** the tool counts this as an inter-module dependency from the importing module to the owning module

#### Scenario: Wildcard import matches another module's package
- **WHEN** an import string ends with `.*` and the package matches a known module's package namespace
- **THEN** the tool counts this as an inter-module dependency

### Requirement: Inter-module dependency evaluation task

The system SHALL include an analysis task that calls the inter-module dependency report tool, interprets the results, and produces architecture findings with recommendations.

The evaluation SHALL consider:
- Instability thresholds: ≤30 (stable), >70 (unstable), 30-70 (balanced)
- Dependency direction violations: modules with low instability depending on modules with high instability
- Hub modules: modules with high Ca that also have high Instability
- Orphan modules: modules with both Ce=0 and Ca=0
- High import counts between module pairs indicating tight coupling

#### Scenario: Normal instability classification
- **WHEN** a module has Instability < 30
- **THEN** the evaluation SHOULD classify it as a stable foundation module

#### Scenario: Unstable module detected
- **WHEN** a module has Instability > 70
- **THEN** the evaluation SHOULD classify it as an unstable leaf module

#### Scenario: Architecture violation detected
- **WHEN** a foundation module (Instability < 30) depends on an application module (Instability > 70)
- **THEN** the evaluation SHOULD flag this as a potential architecture violation

#### Scenario: Hub module with high fragility
- **WHEN** a module has high Ca (> 2 dependents) and high Instability (> 70)
- **THEN** the evaluation SHOULD flag this as a high-risk hub module

#### Scenario: Orphan module detected
- **WHEN** a module has both Ce=0 and Ca=0
- **THEN** the evaluation SHOULD note this as a disconnected module

### Requirement: Instability metric computation

The system SHALL compute Instability automatically per module as `Ce(inter-module) / max(1, Ce(inter-module) + Ca)`, scaled to an integer 0-100.

Ce(inter-module) is defined as the count of unique project modules that this module imports from.

Ca (afferent coupling) is defined as the count of unique project modules that import from this module.

A module with no inter-module dependencies and no dependents SHALL have Instability = 0.

#### Scenario: Foundation module instability
- **WHEN** a module has Ce=1 and Ca=4
- **THEN** Instability = 20 (low — stable, many depend on it)

#### Scenario: Unstable module instability
- **WHEN** a module has Ce=3 and Ca=0
- **THEN** Instability = 100 (high — depends on many, no dependents)

#### Scenario: Balanced module instability
- **WHEN** a module has Ce=2 and Ca=2
- **THEN** Instability = 50 (balanced — intermediate layer)

#### Scenario: Isolated module instability
- **WHEN** a module has Ce=0 and Ca=0
- **THEN** Instability = 0 (no dependencies in either direction)