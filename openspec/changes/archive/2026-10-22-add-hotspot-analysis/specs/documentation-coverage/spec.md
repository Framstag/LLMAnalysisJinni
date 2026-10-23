# Spec Delta

## ADDED Requirements

### Requirement: Hotspot evaluation rendered in documentation

The system SHALL render the results of the hotspot evaluation in the generated documentation output, as a table of the
ranked classes with their change frequency and their complexity, and SHALL name the reference entry of the hotspot
analysis before that table.

#### Scenario: Hotspots are rendered

- **WHEN** the analysis state holds a hotspot evaluation
- **THEN** the documentation includes a section for the hotspots with one row per ranked class, and the section names
  the reference entry that explains the join before its table

#### Scenario: The absence of the history is rendered

- **WHEN** the hotspot evaluation reports the hotspots as absent
- **THEN** the section names the absence with its reason instead of rendering an empty table
