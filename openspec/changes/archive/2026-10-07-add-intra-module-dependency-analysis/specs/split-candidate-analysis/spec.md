# Spec Delta

## Purpose

Proposes where a build module could be separated into smaller modules, by ranking the cheapest ways to cut the module's class reference graph apart. Reports a ladder of candidate separations ordered by the strength of the strongest link that would have to be severed, never a single asserted partition.

## ADDED Requirements

### Requirement: Split candidate tool

The system SHALL provide a tool that returns a ranked ladder of candidate separations for a named module: each entry names the reference edges that would be severed, the weight of the weakest and strongest severed edge, the total severed weight, and the resulting groups.

#### Scenario: Ladder returned for a connected module
- **WHEN** the tool is called with the name of a Java module whose class reference graph is connected
- **THEN** the tool returns a ladder of candidate separations from the cheapest upwards

#### Scenario: Module without a report
- **WHEN** the tool is called with a module name that has no raw module report
- **THEN** the tool returns an empty result and logs a warning

#### Scenario: Report predates weighted references
- **WHEN** the raw module report for the module carries no reference records
- **THEN** the tool reports that the report must be regenerated instead of producing candidates from an unweighted graph

### Requirement: Bottleneck cost orders the ladder

The system SHALL define the cost of a separation as the weight of the reference edge severed at that step, which is the strongest tie between the two groups that the step separates.

The ladder SHALL be ordered by ascending cost, so the cheapest separation comes first.

#### Scenario: One strong link dominates
- **WHEN** two groups are connected by a single high-weight reference edge and otherwise by many low-weight edges
- **THEN** the step that separates those two groups reports the high weight as its cost, and does not appear before the cheaper steps

#### Scenario: Cheapest separation ranks first
- **WHEN** two groups are connected only by low-weight reference edges
- **THEN** separating them has a low bottleneck cost and appears early in the ladder

#### Scenario: Cost grows along the ladder
- **WHEN** the ladder is read from its first entry to its last
- **THEN** the reported cost never decreases

#### Scenario: Structural edges do not affect the cost
- **WHEN** two groups are related by inheritance but by no reference edge
- **THEN** the separation cost does not include the inheritance edge

### Requirement: Ladder is computed without parameters

The system SHALL derive the complete separation ladder from a maximum spanning forest of the module's reference graph, ordered by ascending edge weight.

The computation SHALL be deterministic and SHALL NOT require a resolution, group-count or seed parameter.

#### Scenario: Repeated runs agree
- **WHEN** the tool is called twice for the same module and report
- **THEN** both results are identical

#### Scenario: No group count is requested
- **WHEN** the tool is called
- **THEN** no number of desired groups is required as input, and the ladder covers every level down to the fully separated graph

#### Scenario: Disconnected graphs are handled
- **WHEN** the module's reference graph has more than one component
- **THEN** the ladder starts from that existing partition and continues from there

### Requirement: Total cut weight reported as a secondary figure

The system SHALL report, for each candidate separation, the total weight of all severed reference edges.

The system SHALL state when the total cut weight was not computed because the module exceeded the configured node limit for that computation.

#### Scenario: Total weight accompanies the bottleneck cost
- **WHEN** a candidate separation is returned
- **THEN** both its bottleneck cost and the total weight of severed edges are present

#### Scenario: Oversized module skips the secondary figure
- **WHEN** a module's class count exceeds the configured limit for the exact minimum cut computation
- **THEN** the ladder is still returned, the total cut weight is marked as not computed, and the reason names the limit

### Requirement: Separations may cross package boundaries

The system SHALL derive separations from the class reference graph alone and SHALL NOT constrain a group to package boundaries.

For each resulting group the system SHALL report its size and the package that contributes most of its members, together with the members that do not belong to that package.

#### Scenario: Group spanning several packages
- **WHEN** a group contains classes from more than one package
- **THEN** the result names the dominant package, the number of its members, and the count of members from other packages

#### Scenario: Package structure does not bound the grouping
- **WHEN** a cheaper separation would cut across packages than along package boundaries
- **THEN** the cheaper separation is the one proposed

#### Scenario: Groups are never asserted as the answer
- **WHEN** the tool returns its result
- **THEN** it returns multiple candidate separations with their costs, and does not designate one as the definitive split

### Requirement: Split candidate evaluation task

The system SHALL include a batch analysis task that calls the split candidate tool for all Java modules, interprets the ladders, and produces findings with recommendations, following the existing batch module evaluation pattern.

The task SHALL describe a proposed split with the cost of the separation that produces it.

#### Scenario: All modules evaluated in one task
- **WHEN** the batch split evaluation task runs
- **THEN** it evaluates the ladders of all Java modules in one response

#### Scenario: A proposed split states its price
- **WHEN** the task recommends separating a module
- **THEN** the finding names the bottleneck cost and the total severed weight of that separation

#### Scenario: A module with no cheap separation is reported as such
- **WHEN** every candidate separation of a module has a high bottleneck cost relative to its internal weight
- **THEN** the task states that no cheap separation was found rather than proposing one
