# Spec Delta

## Purpose

Extracts and exposes the reference structure inside a single build module: which top-level classes reference which others, how strongly, and which of those links are structural facts about the type system rather than measured coupling. It is the shared data foundation for god class detection, split candidate analysis and the dependency diagram.

## ADDED Requirements

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

### Requirement: Reference strength counters

The system SHALL measure the strength of a directed reference from one class to another as two independent counters:

- API width: the number of distinct referenced members, identified by owner, member name and descriptor
- Traffic: the number of reference sites in the referencing class's bytecode whose owner is the referenced class

#### Scenario: Repetition does not widen the interface
- **WHEN** a class calls the same method of another class fifty times
- **THEN** traffic is fifty and API width is one

#### Scenario: Distinct members widen the interface
- **WHEN** a class calls three different methods of another class once each
- **THEN** API width is three and traffic is three

#### Scenario: Both counters are reported
- **WHEN** an edge is returned by the graph tool
- **THEN** it carries both the API width and the traffic, and neither is derived from the other

### Requirement: Structural edges are distinguished and exempt from pruning

The system SHALL classify superclass, implemented interface and declared field type references as structural edges, distinct from reference edges.

Structural edges SHALL always be emitted by the diagram and SHALL NOT be subject to any diagram cutoff.

Structural edges SHALL NOT contribute to split separation cost.

#### Scenario: Inheritance edge survives the cutoff
- **WHEN** a diagram cutoff removes every reference edge of a class
- **THEN** its superclass and implemented interface edges are still emitted

#### Scenario: Inheritance does not inflate separation cost
- **WHEN** two groups of classes are separated by a reference edge and are also related by an inheritance edge
- **THEN** only the reference edge contributes to the separation cost

#### Scenario: Declared field type is structural
- **WHEN** a class declares a field whose type is another class in the module
- **THEN** that reference is classified as structural

### Requirement: Graph is filtered to the project namespace

The system SHALL exclude nodes and edges that do not belong to the project namespace before any graph analysis is performed.

The system SHALL report the number of excluded nodes and excluded edges so the omission is visible.

#### Scenario: Shaded dependency classes excluded
- **WHEN** a module report contains classes belonging to a shaded third-party dependency
- **THEN** those classes appear neither as nodes nor as edge targets

#### Scenario: Exclusions are reported
- **WHEN** the graph tool excludes classes outside the project namespace
- **THEN** the result states how many nodes and how many edges were excluded

#### Scenario: Filtering precedes all analyses
- **WHEN** any connectivity, clustering, cut or ranking computation runs
- **THEN** it runs on the filtered graph only

### Requirement: Nodes are top-level classes

The system SHALL represent each node of the graph as one top-level class of the module.

Nested, inner and anonymous classes SHALL contribute their references to the top-level class that encloses them and SHALL NOT appear as nodes.

#### Scenario: Inner class does not become a node
- **WHEN** a top-level class contains four inner classes that reference other classes
- **THEN** the graph contains one node for that top-level class and its edges carry the combined references

#### Scenario: Node count matches top-level classes
- **WHEN** the graph tool returns a graph for a module with 148 top-level classes
- **THEN** the node list has 148 entries

### Requirement: Per-method access counters are recorded

The system SHALL record for every method four counters: internal field accesses, foreign field accesses, internal calls and foreign calls.

A field access or call is internal when its owner is the top-level class that encloses the method, and foreign otherwise.

#### Scenario: Method reading its own class's field
- **WHEN** a method accesses a field declared by its own top-level class
- **THEN** the internal field access counter is incremented and the foreign field access counter is not

#### Scenario: Method reaching into another class
- **WHEN** a method calls a method declared by another top-level class
- **THEN** the foreign call counter is incremented and the internal call counter is not

#### Scenario: Counters are persisted with the method
- **WHEN** the raw module report is written and read again
- **THEN** each method still carries its four access counters

### Requirement: Raw module report carries the weighted reference record

The system SHALL persist the per-class weighted reference record and the per-method access counters in the raw module report.

The system SHALL retain the flat list of referenced type names beside that record, so the existing metric tools keep their existing inputs and outputs for reports written before the record existed and for reports that carry it.

#### Scenario: Existing metric tools unchanged
- **WHEN** any existing Java metric tool that consumes the flat list of referenced type names runs against a report in the new format
- **THEN** its inputs and results are identical to the previous format

#### Scenario: Report written before the weighted record stays readable
- **WHEN** an existing Java metric tool runs against a report that carries no weighted reference record
- **THEN** it returns the same result as before this change, and does not report an absence of references as an absence of dependencies

#### Scenario: Weighted record is available after a round trip
- **WHEN** a raw module report is written and then loaded by a later tool
- **THEN** API width, traffic and the structural classification of each reference are still available
