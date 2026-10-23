# Spec Delta

## Purpose

Counts the version history of a project, so that the code that changes, the code that is old, the code that moves
together and the code that only one person knows can be told apart from the code that is merely there.

## ADDED Requirements

### Requirement: The history of a file is counted

The system SHALL provide a tool that returns, for every file of the project that the history knows, the number of
commits that changed the file, the number of lines added and the number of lines removed over those commits, the
date of the first and the date of the last change, and the authors that changed the file.

#### Scenario: File with several commits is reported

- **WHEN** the tool is called for a project in which a file was changed in three commits by two authors
- **THEN** the result names the file with the commit count three, the added and the removed line count, the first
  and the last change date and the two authors

#### Scenario: File the history does not know is absent

- **WHEN** the tool is called for a project in which a file was never committed
- **THEN** the result holds no row for that file, instead of a row with a count of zero

### Requirement: The history of a module is aggregated

The system SHALL aggregate the counted history per build module, holding the number of files of the module that
changed, the churn of the module, the date of its last change and the number of its authors, so that the vitality of a
module can be read without reading every file of it.

#### Scenario: Module history is aggregated

- **WHEN** the tool is called for a project whose module `core` has four files that changed
- **THEN** the result names `core` with the changed file count four, the churn of the module, the date of its last
  change and the number of its authors

### Requirement: The authorship of a module is reported apart

The system SHALL report for every module the authors that changed it and how many commits each of them made, so that
a module whose authorship is concentrated can be told apart from a module many people work on.

#### Scenario: One author carries the module

- **WHEN** the module `legacy` was changed in nine commits, eight of them by one author
- **THEN** the result names that author with the commit count eight beside the other authors of the module

### Requirement: Files that change together are counted

The system SHALL count the pairs of files that were changed in the same commit and SHALL report the number of commits
such a pair shared, so that the unit of work the history shows can be told apart from the unit the build declares.

#### Scenario: Two files always change together

- **WHEN** two files of the project were changed together in five commits
- **THEN** the result names the pair with the shared commit count five

#### Scenario: A pair with one shared commit is reported

- **WHEN** two files of the project were changed together in exactly one commit
- **THEN** the pair is reported with the shared commit count one, and it is not dropped as noise

### Requirement: A history that cannot be read is reported as absent

The system SHALL report a history that cannot be read as absent with the reason, and SHALL NOT fail the run, and the
reason SHALL name which of the cases occurred: the project is not a repository, the machine holds no git executable,
or the history is truncated.

#### Scenario: Project is not a repository

- **WHEN** the tool is called for a project directory that is not inside a repository
- **THEN** the result reports the history as absent and names that the project is not a repository

#### Scenario: Machine holds no git executable

- **WHEN** the tool is called on a machine where the git executable cannot be run
- **THEN** the result reports the history as absent and names that the git executable is not available

#### Scenario: History is truncated

- **WHEN** the tool is called for a repository whose history is shallow
- **THEN** the result reports the history as absent for the time before the oldest commit it holds, and names that the
  history is truncated

### Requirement: The counted window is bounded and named

The system SHALL count the history of a window of commits, SHALL name that window in the result, and SHALL state what
the bound leaves out, so that two runs of the same project count the same history.

#### Scenario: Window is named in the result

- **WHEN** the tool is called for a project
- **THEN** the result names the window it counted, for example the number of commits or the time span it covered

#### Scenario: A file older than the window is not counted

- **WHEN** a file was last changed before the counted window began
- **THEN** the file holds no row, and the result states that the window leaves out the commits before it

### Requirement: The history evidence is written as CSV

The system SHALL write the counted history as project level CSV reports, one file per view, so that every number the
analysis reasons over can be checked by a reader.

#### Scenario: One file per view

- **WHEN** the history has been counted
- **THEN** the report directory of the history holds one file for the files, one for the modules and one for the
  files that change together

#### Scenario: The rows of a view are in one file

- **WHEN** two views of the history hold different columns
- **THEN** they are written as two files instead of one file with unioned columns
