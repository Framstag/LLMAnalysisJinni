# Spec Delta

## MODIFIED Requirements

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

### Requirement: Split candidate evaluation task

The system SHALL include an analysis task that calls the split candidate tool, interprets the ladders, and produces findings with recommendations, following the existing module evaluation pattern.

The system SHALL evaluate the modules one at a time, so that the request the model is asked with holds the data of a single module and not the data of the whole project.

The task SHALL describe a proposed split with the cost of the separation that produces it.

#### Scenario: All modules evaluated in one task
- **WHEN** the split evaluation task runs
- **THEN** it evaluates the ladders of all Java modules
- **AND** it evaluates them in a step of its own for every module
- **AND** the request that carries the data of a module holds the data of that module alone

#### Scenario: A proposed split states its price
- **WHEN** the task recommends separating a module
- **THEN** the finding names the bottleneck cost and the total severed weight of that separation

#### Scenario: A module with no cheap separation is reported as such
- **WHEN** every candidate separation of a module has a high bottleneck cost relative to its internal weight
- **THEN** the task states that no cheap separation was found rather than proposing one

#### Scenario: The findings of a module are stored in that module

- **WHEN** a module has been evaluated
- **THEN** the findings of that module are stored with that module
- **AND** they are not stored apart from it

## ADDED Requirements

### Requirement: The severed edges of a separation are bounded

The system SHALL bound the number of the severed edges it names in one entry, and SHALL report the
number of the severed edges of that separation beside the named ones, so that a reader knows the
list is a part of the whole. The edges that are named SHALL be the strongest ones, because they are
the ones that decide the price of the separation. The price of the separation SHALL NOT depend on
the bound.

#### Scenario: A separation that severs many edges names only the strongest of them

- **WHEN** a separation severs more edges than the tool names
- **THEN** the entry names the strongest edges of that separation
- **AND** it reports the number of the severed edges beside the named ones

#### Scenario: The price of a separation does not depend on the bound

- **WHEN** the edges of a separation are named only in part
- **THEN** the total severed weight of that separation is still the weight of all of its severed edges
