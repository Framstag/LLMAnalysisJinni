# Spec Delta

## ADDED Requirements

### Requirement: Modernisation strategy rendered in documentation

The system SHALL render the result of the modernisation strategy analysis in the generated documentation output, as one
table per modernisation area, naming the area, the patterns that fit it, the evidence for and against them and the
figures the cost rests on, and SHALL name the reference entry of the strategy analysis before those tables.

#### Scenario: The areas are rendered

- **WHEN** the analysis state holds a modernisation strategy result
- **THEN** the documentation includes a section for the modernisation strategy with one table per area, and each row
  names the area, its patterns, the evidence and the figures of the cost

#### Scenario: The order is rendered as an order and not as a verdict

- **WHEN** the strategy section is rendered
- **THEN** it presents the areas in the order of the counted cost and names no area as the one to start with

#### Scenario: An area type without its evidence is rendered as absent

- **WHEN** the strategy result reports an area type as absent
- **THEN** the section names that area type as absent with its reason, instead of leaving the type out
