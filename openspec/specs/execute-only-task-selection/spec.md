# execute-only-task-selection Specification

## Purpose

Defines the command line contract for restricting an analysis run to a named set of tasks, so that selecting tasks never depends on the order of arguments and never interferes with the workspace directory argument.

## Requirements

### Requirement: Execute-only selection does not consume the workspace directory argument

The `-o` / `--executeOnly` option SHALL take its values without consuming arguments that belong to another option or to a positional parameter, so that the workspace directory can be given before or after the option.

#### Scenario: Option before the workspace directory
- **WHEN** `analyse -o TaskId <workspace directory>` is invoked
- **THEN** the workspace directory SHALL be recognised as the workspace directory argument
- **AND** the run SHALL proceed with the workspace configuration loaded

#### Scenario: Option after the workspace directory
- **WHEN** `analyse <workspace directory> -o TaskId` is invoked
- **THEN** the workspace directory SHALL be recognised as the workspace directory argument
- **AND** the run SHALL proceed with the workspace configuration loaded

#### Scenario: Missing workspace directory is reported as such
- **WHEN** an invocation supplies task ids but no workspace directory argument
- **THEN** the command SHALL fail with a message naming the missing workspace directory argument

### Requirement: Task ids can be supplied as a list or as repeated options

The option SHALL accept several task ids, both as a comma-separated value and as repeated occurrences of the option, and both forms SHALL select the same set of tasks.

#### Scenario: Comma-separated list
- **WHEN** `-o FirstTask,SecondTask` is passed
- **THEN** the selected task ids SHALL contain `FirstTask` and `SecondTask`

#### Scenario: Repeated option
- **WHEN** `-o FirstTask -o SecondTask` is passed
- **THEN** the selected task ids SHALL contain `FirstTask` and `SecondTask`

#### Scenario: Single task
- **WHEN** `-o FirstTask` is passed
- **THEN** the selected task ids SHALL contain only `FirstTask`

#### Scenario: No option passed
- **WHEN** the option is not passed
- **THEN** no task id SHALL be selected and the run SHALL NOT be restricted to a subset of tasks

### Requirement: The selection restricts the run to the named tasks

When task ids are selected, the run SHALL NOT execute any task outside the selection, and it SHALL refuse to run when the selection names a task that is not active.

#### Scenario: No unselected task runs
- **WHEN** an analysis runs with a selection of task ids
- **THEN** no task outside the selection SHALL be executed

#### Scenario: Selection of an inactive task is refused
- **WHEN** the selection names a task whose `active` flag is false
- **THEN** the run SHALL abort before executing any task
