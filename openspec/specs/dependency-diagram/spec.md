# dependency-diagram Specification

## Purpose

Renders the class reference graph of a module as PlantUML source embedded in the analysis state and printed verbatim in the generated documentation, at two levels of detail, with every omission stated. Produces a picture a human reads, without any model involvement in its content.

## Requirements

### Requirement: Dependency diagram tool

The system SHALL provide a tool that emits PlantUML source for a named module into the analysis state.

The tool SHALL NOT require any model-provided content and SHALL produce the same source for the same module and report.

#### Scenario: Diagram source stored in the analysis state
- **WHEN** the tool is called for a module
- **THEN** the PlantUML source is stored under a module-specific key in the analysis state that the engine persists

#### Scenario: Repeated calls are idempotent
- **WHEN** the tool is called twice for the same module
- **THEN** the stored source is replaced, not appended, and the analysis state contains one diagram set for that module

#### Scenario: Module without a report
- **WHEN** the tool is called with a module name that has no raw module report
- **THEN** the tool stores no diagram and reports the reason

### Requirement: Two levels of detail per module

The system SHALL emit a package-level overview diagram for a module and, in addition, class-level diagrams for the module's groups.

The package-level overview SHALL be emitted whenever the module has a class reference graph at all.

The class-level diagrams SHALL be emitted only for groups that fit within the configured budget; groups beyond the budget SHALL be reported as not drawn, with the reason.

#### Scenario: Overview always present
- **WHEN** a module has a class reference graph
- **THEN** its package-level overview diagram is emitted

#### Scenario: Class detail only under budget
- **WHEN** a group contains more classes than the configured node budget
- **THEN** no class-level diagram is emitted for that group and the result states the group size and the budget

#### Scenario: Both levels are distinguishable in the document
- **WHEN** the documentation prints the diagrams of a module
- **THEN** the package-level overview and the class-level diagrams are presented as separate labelled items

### Requirement: Package overview aggregates class-level edges

The package-level overview SHALL draw one node per package of the module and SHALL aggregate the class-level reference weights between packages.

#### Scenario: Aggregated edge weight
- **WHEN** three classes of package P reference classes of package Q
- **THEN** the overview draws one edge from P to Q whose weight is the aggregated weight of those references

#### Scenario: Package diagram node count
- **WHEN** a module has twelve packages
- **THEN** its package-level overview has at most twelve nodes

### Requirement: Cutoff is configured and omissions are stated in the diagram

The system SHALL apply a configured upper bound on node count and a configured minimum edge weight when emitting class-level diagrams.

Every emitted diagram SHALL carry a caption stating how many classes were shown out of the total and how many edges were omitted below the minimum weight.

#### Scenario: Omitted edges are counted
- **WHEN** a class-level diagram omits reference edges below the minimum weight
- **THEN** its caption states the number of omitted edges and the weight threshold applied

#### Scenario: Truncation is never silent
- **WHEN** a diagram shows fewer classes than the group contains
- **THEN** its caption states the shown and total class counts

#### Scenario: A module too large for any diagram is stated
- **WHEN** even the package-level overview exceeds the configured node budget
- **THEN** the system emits no diagram for that module and states that the module exceeded the budget

### Requirement: Structural edges are always drawn

The system SHALL draw every structural edge of the nodes included in a diagram, regardless of any cutoff.

#### Scenario: Inheritance survives a heavy cutoff
- **WHEN** the minimum edge weight excludes all reference edges of a class that extends another class in the diagram
- **THEN** the inheritance edge is still drawn

### Requirement: Diagram is deterministic and model-independent

The system SHALL generate the diagram content without any model-generated input.

Group labels SHALL be derived from group composition: the dominant package of the group and the members that do not belong to it.

#### Scenario: Labels come from the data
- **WHEN** a group is labelled in a diagram
- **THEN** the label names its dominant package and its non-conforming member count

#### Scenario: Model output cannot alter the diagram
- **WHEN** the evaluation text of the task differs between two runs on the same report
- **THEN** the emitted diagram source is unchanged

### Requirement: Diagram source is printed verbatim in the documentation

The documentation template SHALL print the stored diagram source without escaping or reindentation, so that the reader's toolchain can render it.

#### Scenario: Arrows survive template rendering
- **WHEN** the documentation is generated for a module with a stored diagram
- **THEN** the emitted document contains the diagram source with its arrow syntax intact

#### Scenario: Module without a diagram
- **WHEN** a module has no stored diagram
- **THEN** the documentation prints no diagram block for that module and states why none is present

### Requirement: Generated diagram source is validated before being stored

The system SHALL check the generated PlantUML source for structural validity before storing it, and SHALL store a caption explaining the failure instead of invalid source.

#### Scenario: Invalid source is not stored
- **WHEN** the generated source fails the structural check
- **THEN** the system stores no diagram for that module and reports that generation failed
