# Java Reports Subdirectory

## Purpose

Java scan report files (`Java_*.json`) stored in `Java/` subdirectory of workspace root instead of directly in workspace root. Keeps workspace organized, consistent with CSV report subdirectory pattern.

## Requirements

### Requirement: Write reports to Java subdirectory

The system SHALL store generated Java analysis report files in a `Java/` subdirectory of the workspace root rather than directly in the workspace root.

The subdirectory SHALL be created automatically when it does not exist.

#### Scenario: New report creates subdirectory and writes file

- **WHEN** `java_generate_module_analysis_report` called for a Java module
- **THEN** `Java/` subdirectory created in workspace root if missing
- **AND** report file `Java_<moduleName>.json` stored inside `Java/` subdirectory

#### Scenario: Existing report read from subdirectory

- **WHEN** `java_generate_all_module_analysis_reports` called and `Java/<reportId>.json` exists
- **THEN** existing report reused
- **AND** report status `REUSED`

#### Scenario: Report existence checked in subdirectory

- **WHEN** checking if a Java report exists for a module
- **THEN** check performed inside `Java/` subdirectory

#### Scenario: No report returns false

- **WHEN** no `Java_<moduleName>.json` exists in `Java/` subdirectory
- **THEN** report existence returns `false`

### Requirement: Migrate existing workspaces

Existing workspace files SHALL be moved to `Java/` subdirectory.

#### Scenario: jabref files moved

- **WHEN** migration applied to `workspaces/jabref/`
- **THEN** `Java_*.json` files moved from root to `Java/`
- **AND** no `Java_*.json` files left in root

#### Scenario: spring-petclinic file moved

- **WHEN** migration applied to `workspaces/spring-petclinic/`
- **THEN** `Java_Spring_PetClinic_Sample_Application.json` moved to `Java/`

#### Scenario: educational-platform directory renamed

- **WHEN** migration applied to `workspaces/educational-platform/`
- **THEN** `java/` directory renamed to `Java/`