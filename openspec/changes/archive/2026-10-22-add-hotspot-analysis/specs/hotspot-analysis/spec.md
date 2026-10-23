# Spec Delta

## Purpose

Joins how often a class changed with how complex its methods are, so that the parts of a project that are both
complicated and alive are told apart from the parts that are only complicated or only alive.

## ADDED Requirements

### Requirement: The change frequency of a class is counted

The system SHALL provide a tool that returns, for every class of the project that the counted history knows, the
number of commits that changed the file the class was declared in, and the number of lines added and removed in
those commits.

#### Scenario: Class with a changed file is reported

- **WHEN** the tool is called for a project in which the file of a class was changed in four commits
- **THEN** the result names the class with the change count four and the added and removed line count of its file

#### Scenario: Class whose file never changed is absent

- **WHEN** the tool is called for a project in which the file of a class was never committed
- **THEN** the result holds no row for that class, instead of a row with a count of zero

### Requirement: The complexity of a class is taken from the counted metrics

The system SHALL take the complexity of a class from the counted cyclomatic complexity and the counted method size of
the raw module report, and SHALL NOT count the complexity again.

#### Scenario: The counted complexity is used as it stands

- **WHEN** the tool is called for a project whose module report holds the counted complexity of a class
- **THEN** the result names that complexity beside the class, and the counting of the complexity happens in the
  module report and not in this tool

### Requirement: The hotspots are ranked by both figures

The system SHALL rank the classes by their change frequency and their complexity together, and SHALL report both
figures beside the rank, and SHALL NOT apply an absolute threshold that decides the outcome.

#### Scenario: A class that changes often and is complicated comes first

- **WHEN** a class changed in twelve commits and carries a high complexity, and another class changed twice and
  carries a low complexity
- **THEN** the first class is ranked before the second, and both figures are reported beside each rank

#### Scenario: A complicated class that never changes is not a hotspot

- **WHEN** a class carries a high complexity and was never changed
- **THEN** it holds no rank, because the join needs both figures

#### Scenario: The rank is relative to the project

- **WHEN** the first ranked class of a project is read
- **THEN** the result states that it ranks first among the classes of this project, and states no threshold that
  would decide the same outcome in another project

### Requirement: A project without a counted history has no hotspots

The system SHALL report the hotspots as absent with the reason when the counted history of the project cannot be read
at all, and SHALL NOT report an empty ranking as if the project had no hotspots.

#### Scenario: History is absent

- **WHEN** the tool is called for a project whose history was reported as absent, because the project is not a
  repository or because the machine holds no git executable
- **THEN** the result reports the hotspots as absent and names the reason of the absent history

#### Scenario: A truncated history still has hotspots

- **WHEN** the tool is called for a project whose history was reported as truncated, because the copy of the
  repository holds only the recent commits
- **THEN** the result holds the hotspots of the counted window and names that the history is truncated, so that the
  classes changed before the window are left out

### Requirement: The build units left out of the ranking are named

The system SHALL name the build units of a module that lie outside the namespace of the module in the result of the
hotspot tool, with the reason of the exclusion, and SHALL keep the count of the classes of the module beside them.

#### Scenario: An excluded build unit is named

- **WHEN** the tool is called for a project whose module report holds a build unit outside the namespace of the
  module
- **THEN** the result names that build unit and states that it is outside the namespace of the module, and the
  class of that build unit holds no row of the ranking

### Requirement: The hotspot evidence is written as CSV

The system SHALL write the counted hotspots as a project level CSV report, one row per class, with the change
frequency, the complexity and the rank beside each other.

#### Scenario: Hotspot CSV is written

- **WHEN** the hotspots have been counted
- **THEN** a CSV file holds one row per class with the class name, the module, the change count, the complexity and
  the rank

### Requirement: The hotspot evaluation states what the join cannot see

The system SHALL state in the evaluation of the hotspots that the change frequency counts the commits of the counted
window, that a class that was changed together with another class carries the change count of its file, and that a
truncated history leaves the classes of the older commits out.

#### Scenario: The limits are stated in the evaluation

- **WHEN** the hotspot evaluation is read
- **THEN** it states that the change frequency is bound to the counted window of the history and that a truncated
  history leaves out the classes changed before it
