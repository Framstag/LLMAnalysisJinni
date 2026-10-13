# Spec Delta

## ADDED Requirements

### Requirement: Project-level report with several views names each file after its view

A report that has no module context and carries several views whose rows are not interchangeable SHALL write one file per view, named after the view, inside the subdirectory named after the report type.

#### Scenario: View file names the view

- **WHEN** the project-level language overview writes its term span, its module pairs and its intrusion view
- **THEN** the subdirectory named after the report type holds one file per view, and each file name names its view

#### Scenario: Views are not merged into one file

- **WHEN** two views of a project-level report hold different columns
- **THEN** they are written as two files instead of one file with unioned columns

#### Scenario: Module-scoped and report-scoped naming stay unchanged

- **WHEN** a report has a module context
- **THEN** its file body remains the sanitized module name, and a report without module context but with a single view keeps naming its file after the report
