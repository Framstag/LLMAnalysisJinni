# Spec Delta

## Purpose

Verifies the project's own build automatically whenever a commit is pushed or a pull request is updated, so that a change cannot land with the packaged distribution artefact and the test suite unbuilt. Reports the outcome on the commit it verified, making "was this commit built" answerable from the repository instead of from memory.

## ADDED Requirements

### Requirement: Verification run per push and pull request

Every push to the default branch and every pull request commit SHALL start a verification run for that commit.

The result SHALL be attached to the commit that was verified, so that the outcome is readable from the commit or pull request rather than from a separate log store.

#### Scenario: Push to the default branch starts a run

- **WHEN** a commit is pushed to the default branch
- **THEN** a verification run starts for that commit

#### Scenario: Pull request commit starts a run

- **WHEN** a pull request is opened or receives a further commit
- **THEN** a verification run starts for the head commit of that pull request

#### Scenario: The result names the verified commit

- **WHEN** a verification run finishes
- **THEN** its result is available on the commit it ran for, and not only inside the run's own output

### Requirement: Verification runs the project's full build gate

The verification SHALL run the build gate the project documents for proving a release artefact, currently `mvn verify`, so that the packaged distribution artefact is built and the checks bound to that artefact run in the same run.

A subset that only compiles the sources, or that runs the unit tests on the full dependency classpath, SHALL NOT be reported as a successful verification.

#### Scenario: The packaged artefact is built and checked

- **WHEN** a verification run succeeds
- **THEN** the packaged distribution artefact was built during that run and the checks bound to that artefact ran during that run

#### Scenario: A failing check fails the run

- **WHEN** any check of the documented gate fails
- **THEN** the run is reported as failed and names the check that failed

### Requirement: Verification uses the project's Java language level

The verification SHALL compile and test on the language level the project targets, which is declared in the build and currently 25.

A run on a JDK that does not satisfy the targeted language level SHALL NOT be reported as a successful verification of the project.

#### Scenario: The run fails rather than passes on a wrong JDK

- **WHEN** the verification provisioned a JDK below the targeted language level
- **THEN** the run fails instead of reporting a successful verification

#### Scenario: The declared level is the one used

- **WHEN** a verification run starts
- **THEN** the JDK it uses satisfies the language level declared by the build

### Requirement: Verification can be started on demand

A maintainer SHALL be able to start verification for a chosen branch without pushing a commit.

#### Scenario: Manual run for a branch

- **WHEN** a maintainer starts verification on demand for a branch
- **THEN** a verification run starts for the current head commit of that branch
