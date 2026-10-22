# Import Diversity Analysis

## Purpose

Provides tools and evaluation tasks for analyzing import source diversity per module, categorized by external vs internal package prefixes. Enables detection of low internal code reuse, framework lock-in via import concentration, and dependency sprawl.

## Requirements

### Requirement: Import diversity report

The system SHALL provide a tool that returns the distribution of import sources per module, categorized by top-level or second-level package prefix.

The report MUST include separate distributions for production code, test code, and generated code. Each distribution maps import source prefixes to their occurrence count across all classes in the module.

#### Scenario: Successful import diversity report generation

- **WHEN** the LLM calls `java_get_import_diversity_report` with a module name
- **THEN** the system returns 3 `Distribution` objects — one each for production, test, and generated code — mapping import source prefixes to their occurrence count, each entry marked as internal or external

#### Scenario: Module not yet analyzed

- **WHEN** the LLM calls `java_get_import_diversity_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

#### Scenario: Framework import ratio

- **WHEN** the LLM calls `java_get_import_diversity_report` with a module name
- **THEN** each distribution includes entries for top framework prefixes (e.g., `org.springframework`, `org.apache`, `com.fasterxml.jackson`) with their import counts

### Requirement: Import diversity evaluation task

The system SHALL include an analysis task that evaluates import diversity and dependency hygiene per module and produces architecture findings with recommendations.

#### Scenario: High external-to-internal import ratio
- **WHEN** a module has more than 80% of imports resolving to external packages
- **THEN** the evaluation SHOULD flag this as low internal code reuse — the module may be too thin or rely excessively on frameworks

#### Scenario: Dependency concentration on single framework
- **WHEN** more than 50% of a module's external imports resolve to a single external package prefix
- **THEN** the evaluation SHOULD note this as potential framework lock-in

#### Scenario: Java standard library dominance
- **WHEN** more than 40% of external imports are from `java.*` or `javax.*`
- **THEN** the evaluation SHOULD note the module's heavy reliance on standard library types (neutral finding, context-dependent)

### Requirement: Import sources are categorized against the package namespace of the module

Each import source prefix SHALL be marked as internal when it is the package namespace of the module or a prefix of it, and as external otherwise.

The package namespace of a module SHALL be the package of one of the classes of that module. A module whose classes carry no package SHALL have no package namespace, and every import source of that module SHALL be marked as external.

Static imports and imports of `java.lang` SHALL NOT be counted as import sources.

#### Scenario: An import source inside the package namespace is internal

- **WHEN** an import source prefix is the package namespace of the module or a prefix of it
- **THEN** its entry in the distribution SHALL be marked as internal

#### Scenario: An import source outside the package namespace is external

- **WHEN** an import source prefix is neither the package namespace of the module nor a prefix of it
- **THEN** its entry in the distribution SHALL be marked as external

#### Scenario: A module without a package namespace marks every source as external

- **WHEN** the classes of a module carry no package
- **THEN** every import source of that module SHALL be marked as external

#### Scenario: Static imports and java.lang imports are not counted

- **WHEN** a module imports a static member or a member of `java.lang`
- **THEN** that import SHALL NOT appear in the distributions of the module
