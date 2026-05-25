## ADDED Requirements

### Requirement: Import diversity report

The system SHALL provide a tool that returns the distribution of import sources per module, categorized by top-level or second-level package prefix.

The report MUST include separate distributions for production code, test code, and generated code. Each distribution maps import source prefixes to their occurrence count across all classes in the module.

Import source categorization:
- **External**: imports from packages outside the project's own package namespace (e.g., `org.springframework.*`, `java.util.*`, `com.fasterxml.jackson.*`)
- **Internal**: imports from packages within the project's own package namespace
- The project's own package namespace is determined from the module path and source directories; if not determinable, the most common package prefix among classes in the module is used as the internal namespace

#### Scenario: Successful import diversity report generation
- **WHEN** the LLM calls `java_get_import_diversity_report` with a module name
- **THEN** the system returns 3 pairs of `Distribution` objects — one pair (external pkg count + internal pkg count) each for production, test, and generated code

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