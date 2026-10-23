# Spec Delta

## ADDED Requirements

### Requirement: A candidate is reported once per element

The system SHALL report an anchor candidate once per element, naming the module the element is read in and the other
modules whose reports hold it, instead of reporting the element once per module report, so that the budget of a
candidate list is not spent on the same element several times.

#### Scenario: An element that several module reports hold

- **WHEN** an interface of the project appears in the reports of three modules, because the aggregator module holds the
  same classes as its inner modules
- **THEN** the candidate list holds one candidate for that interface, and the candidate names the module it is read in
  and the other two modules

#### Scenario: The ranks are not pushed down by other appearances

- **WHEN** the strongest seam of the project appears in three module reports
- **THEN** it holds one rank, and the seam after it holds the next rank rather than the fourth

#### Scenario: An element of a single module

- **WHEN** an element appears in the report of one module only
- **THEN** its candidate names that module and holds no further module

### Requirement: A production entry point ranks above test code

The system SHALL rank an entry point candidate of production code above an entry point candidate of test or generated
code, so that the budget of the entry point list is spent on the places a caller reaches the product through.

#### Scenario: A benchmark main is not the strongest entry point

- **WHEN** the project holds a production class with a main method and a benchmark harness with a main method and more
  annotations
- **THEN** the production class ranks before the benchmark harness

#### Scenario: A test entry point is still reported

- **WHEN** an entry point candidate is test or generated code
- **THEN** it stays in the list, below the production candidates, and its evidence states that it is test or generated
  code, so that a reader sees what the list holds and does not read a test harness as a product entry
