# Inter-Module Dependency Analysis

## Purpose

Provides tools and evaluation tasks for computing inter-module dependencies across all build modules in a project. Produces per-module afferent coupling (Ca), inter-module efferent coupling (Ce-inter), Instability metric, and a full dependency matrix. Enables detection of architecture violations, hub modules, orphan modules, and instability imbalances.

## Requirements

### Requirement: Inter-module dependency report tool

The system SHALL provide a tool that reads all module analysis reports, builds a namespace-to-module mapping from class qualified names, and classifies each import across all modules as either intra-module, inter-module (project), or third-party.

The tool SHALL produce the inter-module coupling, the afferent coupling and the instability of every module, and SHALL write the dependency matrix as a CSV file.

The tool SHALL return an empty result when fewer than 2 modules have analysis reports.

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

The evaluation SHALL consider the instability of every module, the direction of the dependencies between the modules, the modules that many other modules depend on, the modules that no other module depends on, and the number of imports between a pair of modules.

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

#### Scenario: Balanced module classified

- **WHEN** a module has an Instability between 30 and 70
- **THEN** the evaluation SHOULD classify it as a balanced module

#### Scenario: Tightly coupled module pair detected

- **WHEN** two modules are connected by a high number of imports
- **THEN** the evaluation SHOULD flag the pair as tightly coupled

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

### Requirement: Imports are classified by the package namespace of the target module

The tool SHALL map an import to the module that owns the longest matching package namespace.

The tool SHALL ignore static imports and imports of the JDK standard packages `java.`, `javax.` and `java.lang`.

A wildcard import that ends in `.*` SHALL be matched by the package it names.

The instability of a module SHALL be its inter-module coupling divided by the sum of its inter-module coupling and its afferent coupling, scaled to 0-100 and truncated to a whole number.

#### Scenario: The longest matching namespace wins

- **WHEN** the package namespaces of two modules both match an import
- **THEN** the import SHALL be attributed to the module with the longest matching namespace

#### Scenario: JDK and static imports are ignored

- **WHEN** a module imports a static member or a member of `java.`, `javax.` or `java.lang`
- **THEN** that import SHALL NOT be counted as a dependency

#### Scenario: A wildcard import is matched by the package it names

- **WHEN** an import ends in `.*`
- **THEN** the package it names SHALL be matched against the package namespaces of the modules

#### Scenario: The instability is scaled to 0-100

- **WHEN** a module has an inter-module coupling of Ce and an afferent coupling of Ca
- **THEN** its instability SHALL be Ce/(Ce+Ca) scaled to 0-100 and truncated to a whole number
