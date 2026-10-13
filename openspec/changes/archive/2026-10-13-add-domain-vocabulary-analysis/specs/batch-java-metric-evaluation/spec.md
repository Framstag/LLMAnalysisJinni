# Spec Delta

## MODIFIED Requirements

### Requirement: All Java metrics get batch evaluation support
The system SHALL provide batch evaluation support for every existing Java metric evaluation currently implemented as a per-module loop.

#### Scenario: All Java metric evaluation tasks have batch counterparts
- **WHEN** the batch Java metric capability is implemented
- **THEN** batch evaluation tasks exist for cyclomatic complexity, visibility, inheritance, method complexity, nesting depth, field visibility, class cohesion, coupling, test coverage, circular dependency, method count, documentation ratio, data class detection, boolean parameter analysis, annotation usage, package tangle detection, import diversity, and domain vocabulary

#### Scenario: The vocabulary metric joins the set that writes CSV evidence
- **WHEN** the batch domain vocabulary tool runs
- **THEN** it writes the per-module CSV evidence of its metric under the existing report directory and filename convention
