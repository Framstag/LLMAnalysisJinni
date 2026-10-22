# per-module-analysis-prompt Specification

## Purpose

Keeps the prompt of a task that analyses one module at a time down to what that module needs, so
that the analysis of a project with many modules does not carry the list of all of them into every
model round of every step.

## Requirements

### Requirement: A prompt about one module names that module and the number of the modules

The system SHALL give a prompt that is about one module the name of that module and the number of
the modules of the project, and SHALL NOT give it the list of all the modules.

The prompt SHALL still name the module it is about, so that the analysis is about the module it was
meant to be about.

#### Scenario: The prompt of a per-module task names the module

- **WHEN** the prompt of a task that analyses one module at a time is rendered
- **THEN** it names the module that the step is about

#### Scenario: The prompt of a per-module task states the number of the modules

- **WHEN** the prompt of a task that analyses one module at a time is rendered
- **THEN** it states how many modules the project has

#### Scenario: The prompt of a per-module task does not list the modules

- **WHEN** the prompt of a task that analyses one module at a time is rendered
- **THEN** it does not carry the list of all the modules of the project

#### Scenario: The prompt of a per-module task does not name the other modules one by one

- **WHEN** the prompt of a task that analyses one module at a time is rendered
- **THEN** it does not name a module other than the one the step is about

### Requirement: A prompt about the whole project may carry the list of all the modules

The system SHALL give a prompt that is about the whole project the list of all the modules, because
such a task reasons about the project as a whole.

#### Scenario: The prompt of a whole-project task lists the modules

- **WHEN** the prompt of a task that is about the whole project is rendered
- **THEN** it carries the list of all the modules of the project

#### Scenario: The list of the modules is a part of its own macro

- **WHEN** a prompt carries the list of all the modules
- **THEN** the list comes from the macro that holds the list of all the modules
- **AND** the macro that states the number of the modules does not hold the list
