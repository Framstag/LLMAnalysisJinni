# Spec Delta

## ADDED Requirements

### Requirement: The verification validates the specifications of the project

The verification SHALL run the validation of the specifications of the project, so that a specification that does not validate fails the run.

The validation SHALL cover the main specifications and the changes that are in flight, and SHALL run in the strict mode, so that a specification that reports a warning fails the run as well.

The validation SHALL run with a version of the validation tool that is pinned in the repository.

#### Scenario: The specifications are validated in every run

- **WHEN** a verification run starts
- **THEN** the validation of the specifications runs during that run

#### Scenario: A specification that does not validate fails the run

- **WHEN** a specification does not validate
- **THEN** the run is reported as failed
- **AND** the run names the specification that does not validate

#### Scenario: A warning fails the run as well

- **WHEN** a specification reports a warning in the strict mode
- **THEN** the run is reported as failed

#### Scenario: The changes in flight are validated too

- **WHEN** a change is in flight
- **THEN** its delta specifications are validated during that run

#### Scenario: The validation tool version is pinned

- **WHEN** the validation runs
- **THEN** it runs with a version of the validation tool that is pinned in the repository
