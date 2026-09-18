# Spec Delta

## MODIFIED Requirements

### Requirement: Live TUI shows task execution progress

The system SHALL display a live-updating terminal UI showing all tasks in the DAG with their current execution status. A task that was already successful in an earlier run SHALL be displayed with its successful status from the first frame the TUI paints; no frame SHALL show such a task as pending.

#### Scenario: TUI shows all tasks on start
- **WHEN** analysis begins and at least one task has never been executed successfully
- **THEN** the TUI SHALL display all tasks from the DAG with their initial status (pending)

#### Scenario: Already successful tasks are correct in the first frame
- **WHEN** analysis begins and some tasks were already successful in an earlier run
- **THEN** the first frame the TUI paints SHALL show those tasks as successful
- **AND** no frame painted by the run SHALL show them as pending

#### Scenario: Task status updates live
- **WHEN** a task transitions from pending to running
- **THEN** the TUI SHALL update that task's row to show the running state within 500ms

#### Scenario: Task completion updates live
- **WHEN** a task completes successfully
- **THEN** the TUI SHALL mark that task as successful and show its elapsed time

#### Scenario: Task failure shows inline error
- **WHEN** a task fails
- **THEN** the TUI SHALL mark that task as failed and display the error message inline on the task row

## ADDED Requirements

### Requirement: A run with nothing to execute is reported

When no task of the analysis can be executed because every task is already successful, the system SHALL state that explicitly, SHALL NOT start the live terminal UI, and SHALL NOT print a completion summary that reports task outcomes as if work had been performed. The run SHALL exit with status 0.

#### Scenario: Nothing to execute is stated
- **WHEN** `analyse` runs against a workspace in which every task is already successful
- **THEN** the run SHALL state that no task is runnable
- **AND** the statement SHALL make clear that all tasks are already successful

#### Scenario: No live TUI for a run without runnable tasks
- **WHEN** `analyse` runs in an environment whose stdout is a terminal
- **AND** every task is already successful
- **THEN** the system SHALL NOT start the TUI
- **AND** it SHALL NOT paint a frame listing the tasks as pending

#### Scenario: Exit status of a run without runnable tasks
- **WHEN** `analyse` runs and no task is runnable
- **THEN** the run SHALL exit with status 0

#### Scenario: A workspace with runnable tasks is unaffected
- **WHEN** `analyse` runs and at least one task is runnable
- **THEN** the system SHALL use the display mode it would otherwise use
- **AND** it SHALL NOT state that no task is runnable

### Requirement: TUI runs without restricted native access warnings

The environment the TUI runs in SHALL grant the terminal implementation the native access it requires, so that starting the TUI emits no restricted-native-access warning and the TUI does not become unavailable on a JVM release that blocks restricted methods. The packaged artefact SHALL declare that access in its own metadata.

#### Scenario: No restricted native access warning from the artefact
- **WHEN** the packaged artefact is started
- **THEN** its output SHALL NOT contain a restricted-native-access warning

#### Scenario: Native access declared by the artefact
- **WHEN** the packaged artefact is inspected
- **THEN** it SHALL declare native access for the unnamed module in its metadata
