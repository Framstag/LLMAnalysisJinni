# Spec Delta

## Purpose

Defines how an analysis of a project without a Software Bill of Materials behaves: the absence is a
recorded result, the SBOM tools say what is missing instead of failing silently, and the tasks that
would use dependency data answer with an explicit empty result instead of being rejected.

## ADDED Requirements

### Requirement: An absent SBOM is a result, not a failure

When a project contains no SBOM, the analysis SHALL record that absence in the analysis state, the task
that locates the SBOM SHALL succeed with a negative finding and the path it searched for, and no task
SHALL be marked failed because dependency data is unavailable.

#### Scenario: No SBOM in the project
- **WHEN** the project contains no file the SBOM location task recognises as an SBOM
- **THEN** the analysis state SHALL record that no SBOM was found
- **AND** the location task SHALL be successful
- **AND** the absence SHALL be reported once during the run

#### Scenario: Present SBOM is unaffected
- **WHEN** the project contains an SBOM
- **THEN** the location, load and dependency tasks SHALL behave as they do today

### Requirement: The load task does not claim data that does not exist

The task that loads the SBOM SHALL NOT report loaded dependency data when no SBOM location was found,
and the reason it stores SHALL name the absence.

#### Scenario: Load without a location
- **WHEN** the SBOM location task reported that no SBOM was found
- **THEN** the load task's stored result SHALL state that no SBOM was loaded
- **AND** the stored result SHALL NOT claim dependency data
- **AND** the tag the dependent tasks wait for SHALL be produced only by a run that has an SBOM

#### Scenario: A tool call without an input names the input
- **WHEN** an SBOM tool is called without a file to load
- **THEN** the tool result SHALL name the missing input
- **AND** the model SHALL be able to answer the step without further tool calls

### Requirement: Tasks that need dependency data answer with an empty result

A task whose analysis depends on dependency data SHALL produce a response that conforms to its schema
when no dependency data is available, carrying an explicitly empty result and a reason that names the
missing data. Such a response SHALL NOT be rejected and its task SHALL NOT be marked failed.

#### Scenario: Technology stack without dependency data
- **WHEN** the technology stack task runs and no dependency data is available
- **THEN** its response SHALL validate against its schema
- **AND** the technology list SHALL be empty
- **AND** the reason SHALL name the missing dependency data
- **AND** the task SHALL be successful

#### Scenario: License evaluation without dependency data
- **WHEN** the license evaluation task runs and no dependency data is available
- **THEN** its response SHALL validate against its schema
- **AND** the reason SHALL name the missing dependency data
- **AND** the task SHALL be successful

#### Scenario: No rejection caused by missing data
- **WHEN** a task is rejected while no dependency data is available
- **THEN** the reason for the rejection SHALL NOT be the missing dependency data alone
