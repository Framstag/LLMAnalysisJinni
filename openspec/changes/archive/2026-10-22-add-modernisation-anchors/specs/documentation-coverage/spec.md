# Spec Delta

## ADDED Requirements

### Requirement: Modernisation anchors rendered in documentation

The system SHALL render the modernisation anchors in the generated documentation output, one table per anchor type,
naming the anchor, the element it points at, the evidence it rests on and the candidate patterns that fit it.

#### Scenario: Anchors are rendered per type

- **WHEN** the analysis state holds a modernisation anchor result
- **THEN** the documentation includes a section for the modernisation anchors with a table per anchor type, and each
  row names the anchor, its element, its evidence and its candidate patterns

#### Scenario: An anchor type without its input is rendered as absent

- **WHEN** the modernisation anchor result reports an anchor type as absent
- **THEN** the section names that anchor type as absent with its reason, instead of leaving the type out

#### Scenario: Section carries its pointer line

- **WHEN** the modernisation anchor section is rendered
- **THEN** it names the reference entry that explains the anchor types and the candidate patterns before its tables
