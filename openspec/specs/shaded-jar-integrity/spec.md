# shaded-jar-integrity Specification

## Purpose

Guarantees that the packaged, self-contained distribution artefact is a faithful substitute for running the program from the full dependency classpath: runtime-loaded classes survive packaging, diagnostic output reaches the console, and XML-based parsing works.

## Requirements

### Requirement: Packaged artefact emits console diagnostics

The application started from the packaged distribution artefact SHALL emit the same diagnostic output on stdout/stderr as the same command started from the full dependency classpath. Every diagnostic path that writes to the console SHALL remain effective in the packaged artefact, including progress/status lines, task results, warnings, and errors. The packaged artefact SHALL NOT silently discard diagnostic events because its logging configuration could not be loaded.

#### Scenario: Error is visible when the run cannot start
- **WHEN** `analyse` is started from the packaged artefact with a workspace directory that has no readable configuration
- **THEN** a message naming the cause SHALL appear on stdout or stderr
- **AND** the process SHALL exit with a non-zero status

#### Scenario: Task execution trace is visible
- **WHEN** `analyse` is started from the packaged artefact with `--execution-trace=true`
- **THEN** the console execution trace SHALL appear on stdout
- **AND** the trace SHALL contain a line for each task the run starts

#### Scenario: Normal run reports progress
- **WHEN** `analyse` is started from the packaged artefact against a workspace with pending tasks
- **THEN** at least one progress or start-of-task line SHALL appear on stdout or stderr before the run finishes
- **AND** the run SHALL NOT be silent for its whole duration

### Requirement: Packaged artefact supports XML-based parsing

Components that obtain XML parsers through the platform factory mechanism SHALL work when running from the packaged distribution artefact, so that XML-consuming code paths and XML documents remain usable in the packaged artefact. Obtaining an XML parser SHALL NOT fail because a parser implementation is missing from the artefact.

#### Scenario: XML factories can be created
- **WHEN** the XML input factory and the XML output factory are requested through the platform factory mechanism from the packaged artefact
- **THEN** a usable factory instance SHALL be returned
- **AND** no factory configuration error SHALL be raised

### Requirement: Packaged artefact parses SBOM documents

The SBOM tool SHALL parse an SBOM document when running from the packaged distribution artefact, using the same parser path as a run from the full dependency classpath. The parser path used for SBOM documents is independent of the XML parser availability described above, and SHALL work in the packaged artefact regardless of it.

#### Scenario: SBOM parsing works in the packaged artefact
- **WHEN** the SBOM tool loads an SBOM document from the packaged artefact
- **THEN** the document SHALL be parsed
- **AND** the dependency data of the document SHALL be available
- **AND** loading SHALL NOT fail because a parser implementation is missing from the artefact

### Requirement: Build verification exercises the packaged artefact

The build SHALL verify the packaged distribution artefact itself and SHALL fail when a class that is only reachable through runtime lookup is missing from it. Verification of compiled classes alone SHALL NOT be accepted as evidence that the packaged artefact works.

#### Scenario: Missing runtime-loaded class fails the build
- **WHEN** a class that is required at runtime but is not referenced from compiled bytecode is absent from the packaged artefact
- **THEN** the build verification SHALL fail
- **AND** the failure SHALL name the packaged artefact and the missing capability

#### Scenario: Verification runs the artefact
- **WHEN** the build verification succeeds
- **THEN** the packaged artefact SHALL have been launched as a real process at least once during verification
- **AND** the verification SHALL have asserted its console output and its XML factory availability
