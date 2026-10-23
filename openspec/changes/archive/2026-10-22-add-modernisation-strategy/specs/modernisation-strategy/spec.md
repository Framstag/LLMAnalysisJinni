# Spec Delta

## Purpose

Turns the anchors, the hotspots and the counted evidence of a project into one modernisation area per candidate, with
the patterns that fit it, the evidence for and against them and the cost the change would carry, so that a reader can
choose instead of starting over.

## ADDED Requirements

### Requirement: Modernisation areas are formed

The system SHALL provide an analysis that produces one modernisation area per candidate place, and each area SHALL name
the module it belongs to, the element it points at, the anchor type it rests on and the patterns that fit it.

#### Scenario: Area names the anchor it rests on

- **WHEN** the analysis reads an anchor of the seam type whose interface is implemented by three classes
- **THEN** the area names the interface, the module that declares it, the anchor type it rests on and the patterns
  that fit it

#### Scenario: An anchor without an element becomes an area of its module

- **WHEN** the analysis reads an anchor that points at no element, such as a build unit anchor
- **THEN** the area names the module and carries no element, instead of being dropped

### Requirement: The evidence for and against a pattern is stated

The system SHALL state for every candidate pattern of an area the evidence that supports it and the evidence that
counts against it, and SHALL name what would have to be true for the pattern to be the wrong choice, so that a reader
can disagree with the analysis instead of having to unravel it.

#### Scenario: A pattern carries the evidence against it

- **WHEN** a pattern fits an area whose module has no test coverage
- **THEN** the area states that the pattern is cheap to start and that the module has no tests, and it names the
  missing tests as what would have to be added first

#### Scenario: A pattern without evidence against it says so

- **WHEN** no evidence counts against a pattern of an area
- **THEN** the area says that it found no evidence against that pattern, instead of leaving the reader to guess

### Requirement: The cost of an area rests on counted figures

The system SHALL name the figures the cost of an area rests on, among them the coupling of the module, the price of the
cut of the module and the test coverage of the area, and SHALL report the figures beside the cost.

#### Scenario: The cost names its figures

- **WHEN** an area states a high cost
- **THEN** it names the figures the cost rests on, for example the afferent coupling of the module and the strength
  of the reference that a cut would sever

#### Scenario: A figure the analysis does not hold is not stated

- **WHEN** a figure of the cost is not part of the counted evidence of the project
- **THEN** the area does not state that figure, and it says which figure is missing

### Requirement: The order of the areas is a cost order, not a decision

The system SHALL order the areas by the cost the evidence implies, and SHALL report the figures the cost rests on
beside the order, and SHALL NOT state which area should be modernised first.

#### Scenario: The cheapest area comes first

- **WHEN** one area rests on a cheap cut and a low coupling and another rests on an expensive cut and a high
  coupling
- **THEN** the first area is ordered before the second, and both cost figures are reported beside the order

#### Scenario: The result does not decide

- **WHEN** the analysis reports its areas
- **THEN** it states that the order follows the counted evidence and that the choice of the area to work on is the
  reader's, and it names no area as the one to start with

### Requirement: The patterns come from the catalogue

The system SHALL take the patterns of an area from the catalogue the marker facts define, and SHALL NOT name a pattern
outside that catalogue.

#### Scenario: A pattern outside the catalogue is not named

- **WHEN** the analysis judges an area
- **THEN** every pattern it names is one of the patterns of the catalogue

### Requirement: An area without its evidence is reported as absent

The system SHALL report an area type whose input analysis did not run as absent with the reason, and SHALL NOT
silently leave it out of the result.

#### Scenario: The hotspot analysis did not run

- **WHEN** the analysis runs in a project where the hotspot analysis produced no result
- **THEN** the areas that rest on the hotspots are reported as absent with the reason, and the areas that rest on the
  other evidence are still reported

### Requirement: The strategy analysis reads bounded evidence

The system SHALL read the anchor result, the hotspot result and the counted evidence, and SHALL NOT read the prose
findings of the other evaluation analyses, so that the size of the strategy analysis does not grow with the number of
findings of the project.

#### Scenario: The findings of other analyses do not reach the strategy analysis

- **WHEN** the strategy analysis runs in a project whose evaluation analyses produced many findings
- **THEN** it reads the anchor result, the hotspot result and the counted evidence, and not the findings of the other
  analyses

### Requirement: The strategy analysis states what it cannot see

The system SHALL state the limits of the strategy analysis in its result, among them that the cost of an area rests on
the counted evidence of the Java modules alone and that the analysis holds no knowledge of the business value of a
change.

#### Scenario: The limits are stated

- **WHEN** the strategy analysis reports its areas
- **THEN** it states that the analysis knows no business value of a change and that it ranks by the counted cost only
