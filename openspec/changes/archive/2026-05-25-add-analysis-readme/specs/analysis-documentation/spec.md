## ADDED Requirements

### Requirement: Analysis README exists

The system SHALL include a README.md in the `analysis/software-architecture/` directory documenting the purpose of the analysis and all available analysis tasks.

#### Scenario: README is readable
- **WHEN** the README is opened
- **THEN** it contains a purpose section and a task table

### Requirement: Task table is comprehensive

The README SHALL contain a table listing all analysis tasks with columns for goal, task ID, scope, and quality assessment.

#### Scenario: All tasks listed
- **WHEN** comparing the task table to the tasks in `tasks.yaml`
- **THEN** every task in `tasks.yaml` has a corresponding row in the table

### Requirement: Task table is extendable

The table format SHALL use simple Markdown so new rows can be added without special tools.

#### Scenario: New task added
- **WHEN** a new task is added to `tasks.yaml`
- **THEN** a new row can be added to the table by appending a line
