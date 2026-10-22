# Spec Delta

## MODIFIED Requirements

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

## REMOVED Requirements

**Reason**: The analysis of a module reads the build file of that module, whose name it is given.
The tool that lists the files of a directory was given to it to find the build files of the
directories below the module, and those build files are named by the module list and analysed by
their own step. The analysis made 404 reads of the build files of other modules that way, which
was 47% of the whole analysis.

**Migration**: The tool `filesystem_get_matching_files_in_dir_recursively` stays available for the
tasks that need it. The build file analysis drops it from its whitelist and its prompt drops the
step that uses it.
