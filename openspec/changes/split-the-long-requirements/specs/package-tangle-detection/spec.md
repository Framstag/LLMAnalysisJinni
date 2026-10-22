# Spec Delta

## MODIFIED Requirements

### Requirement: Package-level tangle report

The system SHALL provide a tool that detects circular dependencies between packages within a build module, using the same import-based dependency graph as class-level cycle detection but aggregated at the package level.

The report MUST list each detected package-level cycle with the packages involved, the cycle length, and the classes contributing to the cycle edges.

#### Scenario: Successful package cycle detection

- **WHEN** the LLM calls `java_get_package_tangle_report` with a module name
- **THEN** the system returns a list of detected package-level cycles, each containing the packages involved, the cycle length, and contributing class-level dependencies

#### Scenario: No cycles found

- **WHEN** the LLM calls `java_get_package_tangle_report` with a module name that has no package-level cycles
- **THEN** the system returns an empty list

#### Scenario: Module not yet analyzed

- **WHEN** the LLM calls `java_get_package_tangle_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

## ADDED Requirements

### Requirement: A package depends on another package when a class of it imports a class of the other

A package SHALL be considered to depend on another package when any class of the first package imports any class of the second package.

The package of an import SHALL be derived from the import string by stripping its last segment, the class name. The package of a class SHALL be derived from its qualified name in the same way.

Only the packages of the module itself SHALL be linked, and static imports and imports of `java.lang` SHALL NOT be counted.

#### Scenario: An import between two packages creates a dependency

- **WHEN** a class of package A imports a class of package B
- **THEN** the package level graph SHALL hold a dependency from A to B

#### Scenario: The package of an import is the import without its class segment

- **WHEN** the import `com.framstag.llmaj.tools.java.JavaTool` is classified
- **THEN** its package SHALL be `com.framstag.llmaj.tools.java`

#### Scenario: Only the packages of the module are linked

- **WHEN** a module imports a class of a package that the module does not contain
- **THEN** that import SHALL NOT create a dependency in the package level graph of the module
