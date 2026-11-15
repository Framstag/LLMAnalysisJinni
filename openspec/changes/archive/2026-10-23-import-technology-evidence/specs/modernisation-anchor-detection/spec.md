# Spec Delta

## ADDED Requirements

### Requirement: The technology marker rests on the namespace evidence

The system SHALL report a technology marker from the consolidated external namespace evidence when the marker facts
key that marker by a namespace, and the evidence of the anchor SHALL name the consolidated namespace evidence as the
source it was counted by.

#### Scenario: A legacy namespace without dependency data

- **WHEN** a project holds no dependency data and its external namespaces include one of the legacy namespaces of the
  marker facts
- **THEN** the anchor analysis reports a technology marker that names that namespace
- **AND** the evidence of the anchor names the consolidated namespace evidence as its source

#### Scenario: The namespace is read from the imports

- **WHEN** a technology marker rests on the namespace evidence
- **THEN** it is derived from the namespaces the project's own code imports, and not from a dependency that the
  project declares

### Requirement: The version range marker is absent without dependency data

The system SHALL report the version range half of the technology marker as unavailable when the project holds no
dependency data, with a reason that names the missing data, and SHALL NOT report it as absent without a reason and
SHALL NOT form it from the namespace evidence.

#### Scenario: No dependency data

- **WHEN** the project holds no dependency data and the anchor analysis reports its anchor types
- **THEN** the version range markers are reported as unavailable, and the reason names the missing dependency data
- **AND** no anchor reports a version range of a dependency

#### Scenario: The namespace half is unaffected

- **WHEN** the version range markers are reported as unavailable
- **THEN** the technology markers that the marker facts key by a namespace are still reported, when their namespace is
  part of the consolidated namespace evidence

### Requirement: The two halves of the technology marker are named apart

The system SHALL report the namespace half and the version range half of the technology marker as two parts of one
anchor type, so that a reader sees which part is present and which part is missing, and SHALL NOT report the whole
type as absent when only one of its halves is missing.

#### Scenario: One half is present and one half is missing

- **WHEN** the project holds the namespace evidence but no dependency data
- **THEN** the report holds the namespace markers
- **AND** it states that the version range markers are unavailable, and it does not report the type as absent

#### Scenario: Both halves are missing

- **WHEN** the project holds neither the namespace evidence nor dependency data
- **THEN** the type is reported as absent, and the reason names both missing inputs

### Requirement: The marker knowledge stays in the marker facts

The system SHALL take the namespaces that mark a legacy dependency and the version ranges that mark a dependency at
the end of its life from the marker facts, and SHALL NOT hold them in the tool that consolidates the namespace
evidence.

#### Scenario: The consolidation tool knows no framework

- **WHEN** the namespace evidence is consolidated
- **THEN** no tool names a legacy namespace, a counterpart namespace or a version range

#### Scenario: A namespace the marker facts do not name

- **WHEN** the consolidated evidence holds a namespace that the marker facts do not name as a legacy namespace
- **THEN** no technology marker is reported for it
