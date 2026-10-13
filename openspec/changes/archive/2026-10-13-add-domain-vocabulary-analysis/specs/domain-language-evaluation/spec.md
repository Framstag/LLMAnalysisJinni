# Spec Delta

## Purpose

Judges the language a Java module speaks — how much of it names the domain instead of the plumbing, whether one concept keeps one name, and whether foreign domain terms leak into it — against one project-wide glossary, and stores the findings for the report.

## ADDED Requirements

### Requirement: One project glossary precedes the module judgements

The system SHALL build one ubiquitous-language glossary for the project before any module is judged, holding for each term its meaning, its classification as domain or technical or generic, and the modules that use it, and SHALL store it in the analysis state.

#### Scenario: Glossary is stored

- **WHEN** the glossary task succeeds
- **THEN** the analysis state holds the glossary under its own property

#### Scenario: Glossary task runs once for the project

- **WHEN** the glossary task runs
- **THEN** it runs without a loop over the modules and produces one glossary for the whole project

#### Scenario: Module judgement reads the glossary

- **WHEN** the module language evaluation runs
- **THEN** the glossary is part of its prompt context

#### Scenario: Glossary rests on module descriptions and counted terms

- **WHEN** the glossary task runs
- **THEN** it reads the module purposes and the counted vocabulary, and does not read module source files

### Requirement: Glossary classifies terms with their owning modules

The glossary SHALL give every term it carries exactly one classification and SHALL name the module or modules in which the term is established. A term that different modules establish with different meanings SHALL be recorded as such.

#### Scenario: Term has one classification

- **WHEN** a glossary entry is read
- **THEN** it names the term, its classification and the modules that establish it

#### Scenario: Same term in two meanings is recorded

- **WHEN** two modules use the same term for different concepts
- **THEN** the glossary records term and both meanings with their modules

### Requirement: One batch evaluation judges the language of all modules

The system SHALL provide one non-loop evaluation task that judges the language of all Java modules in one model response, calling the batch vocabulary tool once.

#### Scenario: Single model call judges all modules

- **WHEN** the module language evaluation task runs
- **THEN** it calls the batch vocabulary tool once and judges all modules in one response

#### Scenario: Task depends on the collected reports

- **WHEN** the module language evaluation task is scheduled
- **THEN** it depends on the collected Java module reports and on the module descriptions

### Requirement: Four aspects are judged per module

The evaluation SHALL judge, for each module it reports on, how far the module's vocabulary names its own domain rather than technical plumbing, whether a concept keeps one name inside the module, whether the names follow the code structure, and whether the module's names agree with its stated purpose.

#### Scenario: Domain share is judged in the light of the purpose

- **WHEN** the model judges the technical share of a module
- **THEN** the finding states whether a module with that purpose is expected to speak its own domain, instead of treating a high technical share as a defect on its own

#### Scenario: Naming inconsistency is named

- **WHEN** a concept carries several names in one module
- **THEN** the finding names the term variants it found and the module

#### Scenario: Naming cohesion is judged against the counted overlap

- **WHEN** the model judges whether names follow the code structure
- **THEN** it uses the counted overlap of the linked and the unlinked class pairs and names the value it read

#### Scenario: Purpose divergence is judged in both directions

- **WHEN** the module's vocabulary and its purpose description disagree
- **THEN** the finding names which purpose terms are absent from the code vocabulary or which frequent code terms the description does not mention

### Requirement: Foreign domain terms are judged as a boundary question

The evaluation SHALL report a term that another module establishes and this module uses, and SHALL state for each such term whether the module's own domain, a shared concept or a boundary type explains it.

#### Scenario: Foreign term is reported with its owner

- **WHEN** the model reports a term that another module establishes
- **THEN** the finding names the term and the module that establishes it

#### Scenario: A justified use is not a defect

- **WHEN** a foreign term occurs in a module at a boundary, for example as a transported type
- **THEN** the finding states that the occurrence is expected and does not recommend a rename

#### Scenario: An unexplained use is a finding

- **WHEN** a foreign domain term occurs in a module without a boundary role and without belonging to the module's stated purpose
- **THEN** the finding states the leak, names the term and the recommending module, and gives a recommendation

### Requirement: Findings rest on the counted evidence

The evaluation SHALL express every quantity it reasons about as the value the tool reported, and SHALL name the term with the spelling the tool reported for it.

#### Scenario: A term is quoted as counted

- **WHEN** a finding names a vocabulary term and the tool reported that term
- **THEN** the finding uses the term with the spelling of the tool result

#### Scenario: No invented magnitude

- **WHEN** a finding states how often or how widely a term occurs
- **THEN** the value it states is one the tool reported for that module

### Requirement: The evaluation result is a batch module evaluation

The evaluation SHALL store its result as a top-level object holding the overall reasoning and one entry per reported module with the module name, a reasoning and its findings.

#### Scenario: Result shape

- **WHEN** the module language evaluation task succeeds
- **THEN** the stored result holds a reasoning and a list of module evaluations with findings

#### Scenario: Module without a finding

- **WHEN** a module carries insufficient evidence or no vocabulary
- **THEN** the result either omits it or names it as not judged, and does not invent findings for it

#### Scenario: Grouped findings list the modules

- **WHEN** a finding applies to several modules
- **THEN** the finding names all affected modules explicitly

### Requirement: Rename recommendations are tied to a term and a module

The evaluation SHALL NOT recommend a general improvement of naming. It SHALL recommend a rename only while naming the term to be changed, the module it is changed in, and the concept the term should express.

#### Scenario: Rename recommendation is concrete

- **WHEN** the evaluation recommends a rename
- **THEN** it names the term, the module and the concept

#### Scenario: Vague naming advice is not accepted

- **WHEN** the evaluation recommends improving naming without naming a term, a module and a concept
- **THEN** the recommendation does not meet the analysis contract
