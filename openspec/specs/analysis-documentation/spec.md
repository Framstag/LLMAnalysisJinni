# Analysis Documentation

## Purpose

Documents the purpose, scope, and quality assessment of all analysis tasks in the `analysis/software-architecture/` directory in a discoverable README.md. The task table uses simple Markdown format for easy extension when new analysis steps are added.

## Requirements

### Requirement: Analysis README exists

The system SHALL include a README.md in the `analysis/software-architecture/` directory documenting the purpose of the analysis and all available analysis tasks.

#### Scenario: README is readable
- **WHEN** the README is opened
- **THEN** it contains a purpose section and a task table

### Requirement: Task table is comprehensive

The README SHALL contain a table listing all analysis tasks with columns for goal, task ID, scope, and quality assessment.

#### Scenario: All tasks listed
- **WHEN** comparing the task table to the tasks in tasks.yaml
- **THEN** every task in tasks.yaml has a corresponding row in the table

### Requirement: Task table is extendable

The table format SHALL use simple Markdown so new rows can be added without special tools.

#### Scenario: New task added
- **WHEN** a new task is added to tasks.yaml
- **THEN** a new row can be added to the table by appending a line

### Requirement: Analysis README points to the analysis reference

The analysis README SHALL point to the reference document as the place that explains what an analysis measures and how its result is to be read, instead of explaining an analysis in its own words.

#### Scenario: Pointer is present

- **WHEN** the analysis README is read
- **THEN** it names the reference document and states that it holds the description and the interpretation of the analyses

#### Scenario: No interpretation rule is restated

- **WHEN** the analysis README is searched for a threshold or a metric interpretation
- **THEN** it holds neither, only the pointer to the reference

### Requirement: Analysis README task table is a single table

The analysis README SHALL hold its task table as one Markdown table, with no blank line and no other content between two of its rows, so that it renders as a single table.

#### Scenario: No blank line splits the table

- **WHEN** the task table is rendered
- **THEN** every task row is contiguous with the next row and the table renders as one table

#### Scenario: A new task keeps the table intact

- **WHEN** a task row is added
- **THEN** it is appended as another row of the same table, without a separating blank line

### Requirement: Analysis README directory listing matches the directory

The analysis README SHALL list the files and subdirectories of the analysis directory as they are, so that a reader neither looks for a file that does not exist nor misses one that does.

#### Scenario: Named files exist

- **WHEN** the directory structure section is read
- **THEN** every file it names by name exists in the analysis directory

#### Scenario: A renamed file is listed under its current name

- **WHEN** a file of the analysis directory is renamed
- **THEN** the directory structure section names it under its current name, for example the documentation template as `Documentation.adoc.hbs`

#### Scenario: The reference document is listed

- **WHEN** the directory structure section is read
- **THEN** it names the reference document of the analysis beside the documentation template
