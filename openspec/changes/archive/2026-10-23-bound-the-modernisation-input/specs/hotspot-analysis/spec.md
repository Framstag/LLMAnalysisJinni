# Spec Delta

## ADDED Requirements

### Requirement: A class is reported once with its modules

The system SHALL report a hotspot once per qualified class name, naming the modules whose reports contain that class
and the highest complexity among its appearances, instead of reporting the class once per module.

#### Scenario: A class that appears in several modules

- **WHEN** a class of the project appears in the reports of three modules, because the aggregator module and its
  inner modules hold the same classes
- **THEN** the ranking holds one row for that class, and the row names the three modules and the highest complexity
  of its appearances

#### Scenario: The rank counts the place, not the copies

- **WHEN** a class appears in three modules and no other class of the project changes as often and is as complex
- **THEN** the class holds one rank, and the ranks after it are not pushed down by its other appearances

#### Scenario: A class of a single module

- **WHEN** a class appears in the report of one module only
- **THEN** its row names that module and its complexity
