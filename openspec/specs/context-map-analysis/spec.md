# context-map-analysis Specification

## Purpose
Relates the modules of a project to each other by the language they speak instead of only by the code they import, so that a domain that leaks across a boundary and a boundary that pays for its own translation become distinguishable.

## Requirements

### Requirement: Project-level vocabulary aggregation

The system SHALL aggregate the counted vocabulary of all Java modules into a project-level view holding one row per module and term, and SHALL name for each term the number of modules that use it and which modules those are.

#### Scenario: Term span is reported

- **WHEN** the project vocabulary view is built and the term `realm` occurs in two modules
- **THEN** the term carries the module count two and the names of both modules

#### Scenario: Terms are not merged across modules

- **WHEN** two modules use the same term
- **THEN** each module keeps its own row with its own occurrence count

#### Scenario: Vocabulary of a module without a report is absent

- **WHEN** a module has no Java report
- **THEN** the project view holds no row for it and the tool result names it as skipped

### Requirement: Project-wide vocabulary is reported apart

The system SHALL NOT let a term mark a shared language when more than the configured share of the modules use it, because such a term connects every pair of modules and therefore tells no pair apart. It SHALL report those terms as a view of their own and SHALL NOT let them establish the domain of any module.

#### Scenario: A term most modules use marks no boundary

- **WHEN** a candidate term is used by more modules than the configured share allows
- **THEN** it counts as no module's shared language and is left out of the lexical side of the quadrant

#### Scenario: The project-wide terms are written as their own view

- **WHEN** the project-level evidence is written
- **THEN** one view names every project-wide term with its occurrence count, the number of modules that use it and their names, so that a reader sees what the analysis set aside

#### Scenario: A project-wide term establishes no ownership

- **WHEN** a term is project-wide
- **THEN** no module is reported as its owner, so no module is reported as borrowing it

#### Scenario: A small project has no vocabulary of its own to speak of

- **WHEN** a project holds fewer modules than the configured floor
- **THEN** no term is project-wide, because with the few modules there are, a shared word is the language of the one boundary the project has

#### Scenario: The share is a configuration value

- **WHEN** a workspace is seeded
- **THEN** the share of the modules above which a term counts as project-wide has a documented default value

### Requirement: Lexical overlap between modules

The system SHALL report for each pair of modules their shared candidate domain terms, their token overlap and the terms only one of them uses, and SHALL cover every pair of modules that has vocabulary. The shared terms of a pair are the candidate terms that are not the vocabulary of the whole project, and the overlap is measured over those terms, so that a value and the list beside it say the same thing.

#### Scenario: Pair overlap is reported

- **WHEN** the overlap of two modules is reported
- **THEN** it names the shared terms, the overlap value and the terms exclusive to each side

#### Scenario: A pair without a shared term is reported

- **WHEN** two modules share no candidate domain term
- **THEN** the pair is reported with a zero overlap instead of being omitted

### Requirement: Foreign domain term intrusion

The system SHALL report a term that one module establishes and another module uses, naming the term, the using module, the establishing module, the share of the using module's candidate vocabulary it holds, and whether the establishing module's description carries the term.

#### Scenario: Intruding term is named with its owner

- **WHEN** a module uses a term that another module establishes
- **THEN** the report names the term, the using module, the establishing module and the share of the using module's vocabulary

#### Scenario: A term owned by the module itself is not an intrusion

- **WHEN** a term is established by the module that uses it
- **THEN** it is not reported as an intrusion, whatever its share

#### Scenario: An owner without a description is reported as unknown

- **WHEN** the establishing module has no description
- **THEN** the report states that the owner's description was not available instead of reporting that the term is absent from it

### Requirement: Structural coupling and lexical coupling are reported together

The system SHALL report for each pair of modules the import count between them and their lexical overlap in one record, and SHALL classify the pair by whether each of the two is present.

#### Scenario: Both sides are reported in one record

- **WHEN** a module pair appears in the quadrant report
- **THEN** the record names both modules, the import count between them and their lexical overlap

#### Scenario: A structurally independent pair with shared language is classified as a leak candidate

- **WHEN** two modules share candidate domain vocabulary and no import connects them
- **THEN** the pair is classified as a candidate domain leak

#### Scenario: A coupled pair without shared language is classified as a translation

- **WHEN** two modules are connected by imports and share no candidate domain vocabulary
- **THEN** the pair is classified as a translation, not as a leak

#### Scenario: A pair missing from one side is still reported

- **WHEN** a module pair shares vocabulary and has no import record
- **THEN** the pair is reported with an import count of zero rather than omitted

### Requirement: Project-level evidence is written as CSV

The system SHALL write the project-level evidence as CSV files, one row per fact, under a project-level report directory beside the per-module reports, in a deterministic order and without a wall clock time or run identifier.

#### Scenario: Term rows are written

- **WHEN** the project vocabulary is built
- **THEN** a CSV file holds one row per module and term with its channel, tier and occurrence count

#### Scenario: Term rollup is written

- **WHEN** the project vocabulary is built
- **THEN** a CSV file holds one row per term with its occurrence count, its module count and its modules

#### Scenario: Overlap, intrusion and quadrant are written

- **WHEN** the project-level evidence is built
- **THEN** one CSV file holds the module pairs with their shared and exclusive terms, one holds the intruding terms, and one holds the module pairs with their import count, their lexical overlap and their classification

#### Scenario: Two runs produce identical files

- **WHEN** the project evidence is built twice over the same module reports
- **THEN** the byte content of the files is identical

### Requirement: One overview tool answers for the whole project

The system SHALL provide one tool that returns the project-level overview in a single call: the leading terms with their module span, the intruding terms with their owners, the module pairs with their lexical and structural coupling, the classification counts, and the modules it could not process.

#### Scenario: One call covers the project

- **WHEN** the overview tool is called
- **THEN** its result holds the term span, the intrusion list, the pair list and the classification counts

#### Scenario: The result names what it could not process

- **WHEN** a module has no Java report
- **THEN** the result names it as skipped instead of omitting it

#### Scenario: A project with a single module is answered

- **WHEN** the project holds one Java module
- **THEN** the tool returns the term span and states that no module pair exists, instead of failing

### Requirement: Context map judgement

The system SHALL provide one evaluation task that proposes bounded context candidates from the aggregated evidence and names the relation between them, and SHALL store the result as its own property of the analysis state.

#### Scenario: Context candidates are proposed with their modules

- **WHEN** the context map evaluation succeeds
- **THEN** the stored result holds context candidates, each naming the modules it groups and the language that groups them

#### Scenario: A relation is named between contexts

- **WHEN** two contexts are related in the result
- **THEN** the relation is named by one the result defines, and its evidence is a counted value of the overview

#### Scenario: A module without vocabulary is not placed silently

- **WHEN** a module carries no vocabulary evidence
- **THEN** the result names it as not placed instead of assigning it to a context

#### Scenario: The quadrant backs the relation

- **WHEN** the result names a relation between two contexts
- **THEN** it states whether the pair is structurally coupled and whether it shares vocabulary

### Requirement: A single-module project is answered without invented structure

The context map evaluation SHALL NOT invent a context relation for a project whose modules form no pair, and SHALL state the single context it found.

#### Scenario: One module, one context

- **WHEN** the project holds one Java module
- **THEN** the result names it as the only context and states that no relation exists
