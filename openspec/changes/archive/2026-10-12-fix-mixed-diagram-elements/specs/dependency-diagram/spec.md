# Spec Delta

## ADDED Requirements

### Requirement: A diagram that mixes element families declares the mix

The system SHALL write `allowmixing` into a diagram that draws an element of another family beside its class
elements, before the elements are declared.

The class detail SHALL draw the groups it collapsed as elements of another family, so such a diagram SHALL
declare the mix. A diagram that draws no such element SHALL NOT carry the directive.

#### Scenario: Collapsed groups declare the mix
- **WHEN** a class detail diagram draws a collapsed group beside its classes
- **THEN** the source carries `allowmixing`
- **AND** the renderer accepts the diagram

#### Scenario: A diagram without collapsed groups is unchanged
- **WHEN** a class detail diagram couples to no other group
- **THEN** its source carries no `allowmixing` and is identical to the source emitted before this change

#### Scenario: The package overview declares nothing
- **WHEN** the package overview of a module is emitted
- **THEN** it draws packages only and carries no `allowmixing`

## MODIFIED Requirements

### Requirement: Generated diagram source is validated before being stored

The system SHALL check the generated PlantUML source for structural validity before storing it, and SHALL store
a caption explaining the failure instead of invalid source.

The check SHALL reject a source that puts a class element next to an element of another family without
declaring the mix, because the renderer fixes the diagram type from the first element it reads and rejects the
other family.

#### Scenario: Invalid source is not stored
- **WHEN** the generated source fails the structural check
- **THEN** the system stores no diagram for that module and reports that generation failed

#### Scenario: A mix of element families without the directive is refused
- **WHEN** a source declares a class element and an element of another family and carries no `allowmixing`
- **THEN** the structural check rejects it

#### Scenario: The same source with the directive is accepted
- **WHEN** that source carries `allowmixing`
- **THEN** the structural check accepts it
