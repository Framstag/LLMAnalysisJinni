## ADDED Requirements

### Requirement: Package-level tangle report

The system SHALL provide a tool that detects circular dependencies between packages within a build module, using the same import-based dependency graph as class-level cycle detection but aggregated at the package level.

The report MUST list each detected package-level cycle with the packages involved, the cycle length, and the classes contributing to the cycle edges. A package is considered to depend on another package if any class in the first package imports any class from the second package.

Package prefix matching uses the Java package naming convention: the package name is derived from the import string by stripping the last segment (class name). For example, `com.framstag.llmaj.tools.java.JavaTool` resolves to package `com.framstag.llmaj.tools.java`.

#### Scenario: Successful package cycle detection
- **WHEN** the LLM calls `java_get_package_tangle_report` with a module name
- **THEN** the system returns a list of detected package-level cycles, each containing the packages involved, the cycle length, and contributing class-level dependencies

#### Scenario: No cycles found
- **WHEN** the LLM calls `java_get_package_tangle_report` with a module name that has no package-level cycles
- **THEN** the system returns an empty list

#### Scenario: Module not yet analyzed
- **WHEN** the LLM calls `java_get_package_tangle_report` with a module name that has no analysis report
- **THEN** the system returns an empty list

### Requirement: Package tangle evaluation task

The system SHALL include an analysis task that evaluates package-level cycle reports per module and produces architecture findings with recommendations.

#### Scenario: Package cycle detected
- **WHEN** a module has one or more package-level cycles
- **THEN** the evaluation SHOULD flag each cycle and recommend restructuring package boundaries or extracting shared interfaces

#### Scenario: Large package cycle detected
- **WHEN** a module has a package-level cycle involving more than 3 packages
- **THEN** the evaluation SHOULD flag this as a significant architecture erosion concern requiring refactoring