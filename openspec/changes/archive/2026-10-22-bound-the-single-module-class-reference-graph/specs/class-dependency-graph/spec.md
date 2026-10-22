# Spec Delta

## MODIFIED Requirements

### Requirement: Intra-module class reference graph tool

The system SHALL provide a tool that returns the reference graph of a named module: one node per top-level class, and directed edges from a referencing class to a referenced class.

The result SHALL include the node list, the edge list, and the count of structural and reference edges separately.

#### Scenario: Graph returned for an analysed module
- **WHEN** the tool is called with the name of a Java module that has a current raw module report
- **THEN** the tool returns the nodes, the edges with their weights, and the structural and reference edge counts

#### Scenario: Module without a report
- **WHEN** the tool is called with a module name that has no raw module report
- **THEN** the tool returns an empty result and logs a warning

#### Scenario: Module report predates weighted references
- **WHEN** the raw module report for the module carries no reference records
- **THEN** the tool reports that the report must be regenerated and returns no graph, instead of returning an empty or unweighted graph

## ADDED Requirements

### Requirement: The graph of a single module is bounded

The system SHALL bound the number of the nodes and the edges it lists, and SHALL report the
complete counts beside the listed ones, so that the graph of a module with thousands of classes
still fits into a conversation. The edges that are listed SHALL be the strongest ones, because they
are the ones that describe the structure of the module.

#### Scenario: A large module lists only the strongest edges

- **WHEN** the graph of a module holds more edges than the tool lists
- **THEN** the tool lists the strongest edges of that module
- **AND** it reports the complete number of the nodes, the reference edges and the structural edges beside the lists

#### Scenario: The listed nodes are bounded as well

- **WHEN** the graph of a module holds more top level classes than the tool lists
- **THEN** the tool lists the nodes it can and reports the complete number of the nodes beside the list

#### Scenario: The heaviest couplings are always among the listed edges

- **WHEN** the tool lists the edges of a module
- **THEN** the listed edges include the couplings with the greatest API width of that module
