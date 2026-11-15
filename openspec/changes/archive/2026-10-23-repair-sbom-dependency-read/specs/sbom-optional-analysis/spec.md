# Spec Delta

## MODIFIED Requirements

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
- **THEN** the rules of this capability that describe an absent SBOM SHALL NOT apply to it
- **AND** the location task SHALL record its path and the load task SHALL load it
- **AND** the tasks that read the dependency data SHALL rest on the dependencies the read of that SBOM reported

#### Scenario: A present SBOM whose read yields nothing is not an absent SBOM

- **WHEN** the project contains an SBOM and its dependency read reports no dependency
- **THEN** the analysis state SHALL record the reason the read reported
- **AND** it SHALL NOT record that the project contains no SBOM
- **AND** the tasks that read the dependency data SHALL answer with an empty result and that reason

#### Scenario: A stored reason names the condition that holds

- **WHEN** a task stores a reason for the absence of dependency data
- **THEN** the reason SHALL name the condition the read reported, and SHALL NOT state that the project declares no
  dependency when the read reported that it could not resolve the graph
