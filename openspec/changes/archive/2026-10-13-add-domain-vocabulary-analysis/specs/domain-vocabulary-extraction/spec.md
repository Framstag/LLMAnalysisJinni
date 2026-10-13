# Spec Delta

## Purpose

Turns the identifiers that the raw Java module reports already hold into counted, classified vocabulary evidence per module, so that the language a module speaks can be measured and every number the model reasons over can be audited by a human.

## ADDED Requirements

### Requirement: Vocabulary extracted from the raw module reports

The system SHALL derive the vocabulary of a module from the module report the Java report collection already wrote, without parsing the module's source or class files again. The extracted evidence SHALL cover class and interface names, method names, field names, package name segments and the documentation text of classes and methods.

#### Scenario: Vocabulary derived from a stored report

- **WHEN** the vocabulary of a module is computed and a raw report for that module exists
- **THEN** the vocabulary is derived from that report and no source or class file of the module is read

#### Scenario: Every channel is covered

- **WHEN** the vocabulary of a module is computed
- **THEN** a name that occurs only as a field name, only as a method name, only as a type name, only as a package segment or only in documentation is part of the evidence and is reported with the channel it came from

#### Scenario: Method name heads are kept as verbs

- **WHEN** a method name is split into tokens
- **THEN** its first token is counted additionally as the method's verb

### Requirement: Identifier tokens are split deterministically

The system SHALL split identifiers into lowercase tokens at camel case boundaries, at underscores and at digits, SHALL drop single character tokens and SHALL drop Java keywords. The same identifier SHALL always yield the same tokens.

#### Scenario: Camel case and separators split a name

- **WHEN** the identifiers `ProjectBuilder`, `project_builder` and `projectBuilder2` are tokenized
- **THEN** each of them yields the tokens `project` and `builder`

#### Scenario: Keywords and single characters are dropped

- **WHEN** the identifiers `publicName`, `aToken` and `a` are tokenized
- **THEN** `publicName` yields only `name`, `aToken` yields only `token`, and `a` yields no token

#### Scenario: Splitting is repeatable

- **WHEN** the same module report is tokenized twice
- **THEN** both runs yield the same tokens with the same counts

### Requirement: Every token is classified against a versioned lexicon

The system SHALL classify every token into exactly one tier: technical, generic, noise or unclassified. The lexicon SHALL be a versioned, editable list beside the analysis, and the tool result and the CSV evidence SHALL name the lexicon version that classified the terms. A token the lexicon does not list SHALL be classified as unclassified.

#### Scenario: Lexicon entry classifies a token

- **WHEN** `impl` is listed as technical and `result` as generic in the lexicon
- **THEN** both tokens are reported in their tier and no longer count as candidate domain vocabulary

#### Scenario: Unlisted token is a candidate

- **WHEN** a token is not listed in the lexicon
- **THEN** it is classified as unclassified and counts as candidate domain vocabulary

#### Scenario: Lexicon version is named

- **WHEN** vocabulary evidence is returned or written
- **THEN** it names the version of the lexicon that produced the classification

### Requirement: Vocabulary evidence is written as CSV

The system SHALL write the post-processed vocabulary evidence of a module as CSV files under the workspace directory, following the existing report directory and filename convention, one row per fact and one row per distinct term.

#### Scenario: Term list written

- **WHEN** the vocabulary of module `core-lib` is computed
- **THEN** `DomainVocabulary/core-lib.csv` holds one row per distinct term with its channels, its tier and its occurrence count

#### Scenario: Naming cohesion evidence written

- **WHEN** the vocabulary of module `core-lib` is computed
- **THEN** `NamingCohesion/core-lib.csv` holds one row per class pair considered, naming both classes, whether they reference each other, and their token overlap

#### Scenario: Module metrics written

- **WHEN** the vocabulary of module `core-lib` is computed
- **THEN** `DomainLanguageMetrics/core-lib.csv` holds one row per metric with its name and value

#### Scenario: Same convention as existing reports

- **WHEN** a CSV file of the vocabulary analysis is resolved
- **THEN** it sits in a subdirectory named after its report type and its filename body is the sanitized module name

### Requirement: CSV evidence preserves what the metrics drop

The CSV evidence SHALL hold a row for every distinct term, including terms of the noise and technical tiers, and SHALL preserve the tier of each term so that a wrong lexicon entry stays visible. Metrics SHALL be computed from the technical, generic and unclassified tiers only.

#### Scenario: Noise term is kept in the CSV

- **WHEN** a term is classified as noise and is therefore not counted as candidate domain vocabulary
- **THEN** the term still has a row in the term CSV carrying its tier and its occurrence count

#### Scenario: Wrong classification is visible

- **WHEN** a term that belongs to the domain is listed as technical in the lexicon
- **THEN** its row is present in the term CSV with the tier technical, so the entry can be found and corrected

#### Scenario: A term is not silently absent

- **WHEN** a term occurs in a module and the term CSV of that module is read
- **THEN** the term has exactly one row

### Requirement: CSV evidence is stable across runs

The system SHALL write CSV evidence in a deterministic order and SHALL NOT write a wall clock time, a run identifier or a random value into it, so that two runs over the same report produce identical files.

#### Scenario: Row order is deterministic

- **WHEN** the vocabulary of a module is computed twice
- **THEN** both CSV files hold their rows in the same order

#### Scenario: Two runs produce identical files

- **WHEN** the same module report is processed in two different runs
- **THEN** the byte content of the term CSV, the naming cohesion CSV and the metrics CSV is identical

### Requirement: Module vocabulary metrics

The system SHALL report for each module the number of distinct candidate domain tokens, the share of the technical tier and of the generic tier in the classified occurrences, the share of the occurrences held by the five most frequent candidate tokens, the mean token overlap of reference-linked class pairs minus the mean token overlap of unlinked class pairs, and the share of the purpose and architecture vocabulary that occurs in the module's code vocabulary and the other way round.

#### Scenario: Metrics are reported per module

- **WHEN** the vocabulary of a module is computed
- **THEN** the result names the distinct candidate token count, the technical share, the generic share, the top five term share, the naming cohesion and both coverage shares

#### Scenario: Naming cohesion compares linked with unlinked pairs

- **WHEN** the naming cohesion of a module is reported
- **THEN** it is the difference between the mean token overlap of class pairs connected by a class reference and the mean token overlap of the pairs that are not connected

#### Scenario: Purpose vocabulary is compared with code vocabulary

- **WHEN** the vocabulary of a module is computed and a purpose or architecture description exists for the module
- **THEN** the result names which purpose terms are absent from the code vocabulary and which of the module's most frequent terms are absent from the description

#### Scenario: A module without a description is not invented

- **WHEN** the vocabulary of a module is computed and neither a purpose nor an architecture description exists
- **THEN** the coverage metrics are reported as unavailable instead of as zero

### Requirement: Modules too small for a judgement are named as such

The system SHALL NOT report the vocabulary metrics of a module whose candidate domain token count is below the configured minimum, and SHALL report such a module as carrying insufficient evidence, naming the count it found and the candidate terms it has.

#### Scenario: Small module carries insufficient evidence

- **WHEN** a module's candidate domain token count is below the configured minimum
- **THEN** its result carries the insufficient evidence state, the count it found and its leading candidate terms, and carries no share, no coverage value and no naming cohesion

#### Scenario: The reason for the missing judgement is visible

- **WHEN** a reader of the result of such a module asks why it was not judged
- **THEN** the result names the count, the minimum it is compared against, and the terms the module does have

#### Scenario: Minimum is a configuration value

- **WHEN** a workspace is seeded
  
- **THEN** the minimum candidate domain token count has a documented default value

### Requirement: One batch tool reports the vocabulary of all modules

The system SHALL provide one tool that returns the compact vocabulary rollup for all Java modules in one call: the metrics per module, the most frequent candidate terms per module and the list of modules it could not process.

#### Scenario: One call covers all modules

- **WHEN** the batch vocabulary tool is called
- **THEN** its result holds one entry per Java module of the analysis state

#### Scenario: The result stays compact

- **WHEN** the batch vocabulary tool is called
- **THEN** its result holds the metrics, the leading terms and the tier totals per module, and neither the full term list nor the raw module report

#### Scenario: An unprocessable module is named

- **WHEN** the vocabulary of a module cannot be computed
- **THEN** the result names that module with the reason instead of omitting it

### Requirement: Modules without a Java report are reported as skipped

The system SHALL NOT evaluate a module that has no Java report, and SHALL name such a module in the skipped part of the batch result.

#### Scenario: Non-Java module is skipped and named

- **WHEN** a module of the analysis state is not written in Java and therefore has no raw report
- **THEN** the batch result names it as skipped and holds no metrics for it

#### Scenario: Full term list stays on disk

- **WHEN** the batch vocabulary tool is called
- **THEN** the full term list of each module remains readable in its CSV file

### Requirement: Counting is done in the tool, not by the model

The system SHALL produce every count, share and overlap of the vocabulary analysis in the engine, and SHALL NOT expect a value that the model estimates. The lexicon and the thresholds are the only values the analysis reads from configuration and data files.

#### Scenario: Values originate from the engine

- **WHEN** a vocabulary metric reaches the model
- **THEN** it was computed from the module report by the engine

#### Scenario: Model input is evidence

- **WHEN** the model judges a module's language
- **THEN** it receives counted terms and shares, and it decides meaning, not magnitude
