# Spec Delta

## Purpose

Ranks the classes of a module by how far they stand out from their peers on size, cohesion, foreign-data access, nesting and coupling, so that an oversized and incohesive class can be told apart from a large but well-factored one. Reports relative position within the module rather than an absolute verdict.

## ADDED Requirements

### Requirement: God class ranking tool

The system SHALL provide a tool that returns a ranked list of the classes of a named module, best candidate first, limited to a configurable number of entries.

Each entry SHALL name the class, its rank and the total number of ranked classes, its combined score, its per-factor percentile, and the single factor that contributed most to its score.

#### Scenario: Ranking returned for an analysed module
- **WHEN** the tool is called with the name of a Java module that has a current raw module report
- **THEN** the tool returns a ranked list where every entry carries rank, score, per-factor percentiles and the dominant factor

#### Scenario: Module without a report
- **WHEN** the tool is called with a module name that has no raw module report
- **THEN** the tool returns an empty result and logs a warning

#### Scenario: Result size is bounded
- **WHEN** the module has more classes than the configured entry limit
- **THEN** the tool returns the highest ranked entries up to the limit and states the total number of classes considered

### Requirement: Ranking factors

The system SHALL score each class on all of the following factors:

- size: the sum of the cyclomatic complexity of its methods
- cohesion: the fraction of method pairs that share at least one accessed field
- foreign-data access: the number of accesses to fields declared outside the class
- nesting: the greatest nesting depth among its methods
- method size: the greatest lines-of-code value among its methods
- coupling: afferent and efferent coupling of the class within the module

#### Scenario: Every factor is reported separately
- **WHEN** a class appears in the ranking
- **THEN** its percentile is reported for each factor, not only the combined score

#### Scenario: Cohesion is derived from per-method field access
- **WHEN** two methods of a class both access at least one field of that class
- **THEN** the pair counts as cohesive for the cohesion factor

#### Scenario: A large cohesive class is not ranked highly
- **WHEN** a class has high size but its methods share fields
- **THEN** its cohesion percentile is high and its combined rank reflects that

### Requirement: Percentile ranking instead of thresholds

The system SHALL express every factor as the class's percentile within the module.

The system SHALL NOT use absolute thresholds to decide whether a class is a god class.

The system SHALL NOT return a boolean verdict for a class.

#### Scenario: No absolute cutoff
- **WHEN** the ranking is produced, whatever the module's absolute values
- **THEN** the result contains percentiles and ranks only, with no pass or fail classification

#### Scenario: Percentiles are module-relative
- **WHEN** the same class is ranked in two modules with different overall sizes and complexities
- **THEN** its percentiles differ between the two modules, because they are relative to their peers

#### Scenario: Weighting is visible
- **WHEN** a class is ranked
- **THEN** the result names which factor drove its score, so a reader can disagree with the ranking

### Requirement: Cohesion factor is described as an approximation

The system SHALL describe the cohesion factor as a TCC-like approximation derived from per-method access counters, and SHALL NOT claim to compute the exact TCC metric.

#### Scenario: Report does not overclaim
- **WHEN** the ranking or its evaluation text describes the cohesion factor
- **THEN** it is described as an approximation, not as TCC

### Requirement: God class evaluation task

The system SHALL include a batch analysis task that calls the god class ranking tool for all Java modules, interprets the rankings, and produces findings with recommendations, following the existing batch module evaluation pattern.

The task SHALL group findings common to several modules once and SHALL list every affected module explicitly in such a grouped finding.

#### Scenario: All modules evaluated in one task
- **WHEN** the batch god class task runs
- **THEN** it evaluates the rankings of all Java modules in one response

#### Scenario: Grouped finding names its modules
- **WHEN** a finding applies to several modules
- **THEN** the finding text lists every affected module by name

#### Scenario: Findings are graded
- **WHEN** the task reports a finding
- **THEN** the finding carries urgency and criticality values and does not state a pass or fail verdict for the module
