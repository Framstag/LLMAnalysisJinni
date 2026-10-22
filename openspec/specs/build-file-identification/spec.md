# build-file-identification Specification

## Purpose

Names the build file of a module from the build system of the project and the path of the module,
so that the analysis of a module reads the build file of that module instead of probing the project
for it.

## Requirements

### Requirement: The build file of a module is named by the analysis

The system SHALL name the build file of the module that is analysed, derived from the build system
of the project and the path of the module, and SHALL give that name to the analysis of the module.
The name SHALL be the one the project uses for the build file of that build system.

#### Scenario: The build file of a module is named
- **WHEN** a module of a project that uses a build system is analysed
- **THEN** the analysis of that module is told the name of the build file of the module
- **AND** the name is the one the build system of the project uses

#### Scenario: The root module has the build file at the project root
- **WHEN** the module that is analysed is the root module of the project
- **THEN** the analysis of that module is told the name of the build file without a module path in front of it

#### Scenario: A module with a path has the build file below that path
- **WHEN** the module that is analysed has a path inside the project
- **THEN** the analysis of that module is told the name of the build file below that path

### Requirement: The build file analysis does not probe for the build file

The system SHALL NOT ask the analysis of a module to check whether a file exists before it reads
it, and SHALL NOT ask it to search the project for the build file of a module, because the name of
that file is known.

The system SHALL ask the analysis of a module to read the build file of that module and only that
build file. The build files of the modules below are known from the module list, and every one of
those modules has an analysis of its own.

#### Scenario: The analysis is not asked to check a file before reading it
- **WHEN** the analysis of a module is prepared
- **THEN** it is not told to check whether a file exists before it reads that file

#### Scenario: The analysis is not asked to search for the build file
- **WHEN** the analysis of a module is prepared
- **THEN** it is not told to locate the build file of the module
- **AND** it is told which build file to read

#### Scenario: The analysis reads only the build file of the module

- **WHEN** the analysis of a module is prepared
- **THEN** it is told to read the build file of the module and not the build files of the modules below it

#### Scenario: The modules below are not read again

- **WHEN** the analysis of a module reads a build file
- **THEN** the build file it reads is the one of the module that is analysed
- **AND** it does not read the build file of another module
