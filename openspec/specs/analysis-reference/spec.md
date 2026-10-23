# Analysis Reference

## Purpose

Explains, for every analysis the software architecture report renders, what the analysis measures, how its
result is to be read, what its weights mean and where its limits are, so that a reader of the report can
interpret the numbers instead of guessing at them.

## Requirements

### Requirement: Analysis reference document exists

The system SHALL include one reference document in the documentation directory of the software architecture
analysis that describes what each documented analysis measures and how its result is to be read.

#### Scenario: Reference document is present

- **WHEN** the documentation directory of the software architecture analysis is listed
- **THEN** it contains the reference document beside the documentation template

#### Scenario: Reference is usable as report content

- **WHEN** the reference document is opened
- **THEN** it is AsciiDoc text that can be rendered as a section of the generated report

### Requirement: One entry per documented analysis

The reference document SHALL hold one entry per analysis, and SHALL hold an entry for each analysis of the documented
set of the analyses.

#### Scenario: All documented analyses have an entry

- **WHEN** the reference document is compared with the evaluation sections of the generated report
- **THEN** each analysis in the documented set has exactly one entry and the set of entries holds no analysis outside it

#### Scenario: Number of entries matches the documented set

- **WHEN** the entries of the reference document are counted
- **THEN** the count equals the number of analyses in the documented set

#### Scenario: The documented set names the analyses

- **WHEN** the documented set of the analyses is read
- **THEN** it names the java metric evaluations, the inter-module dependency analysis, the class reference structure
  and split candidates, the god class ranking, the dependency diagrams, the domain vocabulary and domain language
  evaluations, the context map analysis, the modernisation anchor analysis, the hotspot analysis and the
  modernisation strategy analysis

#### Scenario: Vocabulary metrics are explained where the report uses them

- **WHEN** the entry for the domain vocabulary evaluation is read
- **THEN** it names what each measured share is, that the technical share has no threshold of its own and is judged against the purpose of the module, and that identifier analysis sees neither string literals nor configuration keys nor message texts

#### Scenario: Lexicon and thresholds are named with their seeded values

- **WHEN** the steering table of the domain vocabulary entry is read
- **THEN** it names the lexicon file and the minimum candidate token count, and the value a workspace is seeded with for each of them

#### Scenario: Context map entry explains the quadrant

- **WHEN** the entry for the context map analysis is read
- **THEN** it names the two axes of the quadrant, states that a pair sharing vocabulary without an import is a candidate domain leak and not a finding of fact, and states what the intrusion share counts

#### Scenario: Anchor entry explains the anchor types

- **WHEN** the entry for the modernisation anchor analysis is read
- **THEN** it names the anchor types, states what evidence each type rests on, and states that the strength of an anchor is a rank within the project and not a threshold

### Requirement: Entry states what is measured

Each entry SHALL state what the analysis measures, in which unit, and what the measurement counts or leaves out.

#### Scenario: Unit and selection are stated

- **WHEN** an entry is read
- **THEN** it names the quantity the analysis measures, the unit of the reported value, and which code the
  measurement covers, for example production, test or generated code

### Requirement: Entry states how the result is to be read

Each entry SHALL state how its result is to be read: whether a high or a low value is the problem, whether the
value is absolute or relative, and the thresholds the evaluation applies.

#### Scenario: Direction is stated

- **WHEN** an entry is read
- **THEN** it states which direction of the value the analysis treats as the problem

#### Scenario: Scale kind is stated

- **WHEN** an entry is read
- **THEN** it states whether the value is absolute, a share, a ratio, a percentile within the module or a
  count of occurrences

#### Scenario: Thresholds are stated

- **WHEN** an entry is read
- **THEN** every threshold the evaluation of that analysis applies is named with its value

### Requirement: Entry states what the weights mean

For every analysis whose result carries a weight, each entry SHALL state what that weight weighs.

#### Scenario: Weight has a meaning

- **WHEN** an entry of an analysis that reports weights is read
- **THEN** it states what the weight counts, for example the number of references between two classes or the
  number of imports between two modules

#### Scenario: Weights without one are not claimed

- **WHEN** an entry of an analysis that reports no weights is read
- **THEN** it makes no statement about a weight

### Requirement: Entry states the caveats of the metric

Each entry SHALL state what the analysis cannot see, so that a reader does not read more into a value than the
metric supports.

#### Scenario: Approximation is named as such

- **WHEN** an entry describes a metric that approximates another metric
- **THEN** it states that the value is an approximation and not the exact metric

#### Scenario: Detection by convention is named

- **WHEN** an entry describes an analysis that classifies code by a convention, for example test code by its
  file name
- **THEN** it states that the classification follows a convention and can be wrong for code that does not
  follow it

### Requirement: Entry carries a steering table

Each entry SHALL carry a table naming the values that steer the analysis and, for each value, where it comes
from and, for a configuration property, the value a workspace is seeded with.

#### Scenario: Source of a value is distinguishable

- **WHEN** the steering table of an entry is read
- **THEN** each row names the value, what it bounds, and whether it comes from the workspace configuration or
  is a fixed value of the analysis

#### Scenario: Seeded value is named

- **WHEN** a steering table names a configuration property
- **THEN** it also names the value the workspace configuration is seeded with, and that value matches the
  seeded value in the engine

### Requirement: Dependency diagram entry keeps the diagram facts

The entry for the dependency diagrams SHALL state that every emitted diagram names its own omissions in its
caption and that a diagram which could not be drawn is reported with its reason.

#### Scenario: Captions are described

- **WHEN** the dependency diagram entry is read
- **THEN** it states that a caption names how many classes the diagram shows out of the total and how many
  edges were omitted below the minimum weight

#### Scenario: Undrawn diagrams are described

- **WHEN** the dependency diagram entry is read
- **THEN** it states that a group above the node budget, or a module whose class name cannot be written into
  the diagram syntax, is reported with its reason instead of being drawn or stored broken

### Requirement: God class entry keeps the ranking semantics

The entry for the god class ranking SHALL state that every factor is a percentile within the module, that no
absolute threshold decides the outcome, that the ranking produces no pass or fail verdict, and that the
cohesion factor is an approximation.

#### Scenario: Percentile position is stated instead of a verdict

- **WHEN** the god class entry is read
- **THEN** it states that every factor is a percentile within the module, that no absolute threshold decides
  the outcome, and that the ranking produces no pass or fail classification

#### Scenario: A large cohesive class is not a candidate

- **WHEN** the god class entry is read
- **THEN** it states that a large class whose methods work on its own state is not a god class candidate,
  because its cohesion percentile is high

#### Scenario: The cohesion factor is not overclaimed

- **WHEN** the god class entry is read
- **THEN** the cohesion factor is described as an approximation, not as the exact metric

### Requirement: Reference is rendered as a chapter of the report

The generated report SHALL render the reference document once as a chapter of its own.

#### Scenario: Chapter is rendered once

- **WHEN** the report is generated
- **THEN** it holds one chapter rendering the reference document, and the entries appear exactly once

#### Scenario: Chapter is rendered independently of the analysis results

- **WHEN** a report is generated from a run that produced no evaluation result for a documented analysis
- **THEN** the chapter still describes that analysis

### Requirement: Reference carries no per-run content

The reference document SHALL state the metrics of the analyses and SHALL NOT carry numbers of a single run or
text produced by a model.

#### Scenario: No per-run numbers

- **WHEN** the reference document is read
- **THEN** it holds no count, no module name and no value of a concrete analysis run

#### Scenario: Same chapter on every run

- **WHEN** two reports are generated from two different runs
- **THEN** their reference chapters are identical

### Requirement: Reference is the single place stating the interpretation rules

The reference document SHALL be the only place in the repository that states the interpretation rules of an
analysis, and the unreferenced per-module evaluation prompts SHALL not exist.

#### Scenario: Thresholds are not restated elsewhere

- **WHEN** the README of the software architecture analysis is read
- **THEN** it carries no threshold and no metric interpretation of an analysis, only a pointer to the reference

#### Scenario: Superseded prompts are gone

- **WHEN** the prompt directory of the software architecture analysis is listed
- **THEN** no per-module evaluation prompt that no task definition references remains

### Requirement: Anchor entry keeps the candidate semantics

The entry for the modernisation anchor analysis SHALL state that a candidate pattern is a pattern that fits the
evidence of the anchor and not a decision, and SHALL name the catalogue the patterns are taken from.

#### Scenario: Candidate pattern is not a decision

- **WHEN** the anchor entry is read
- **THEN** it states that a candidate pattern is one the analysis matched to the evidence of the anchor, that the
  reader decides, and that the analysis states what would make the pattern the wrong choice

#### Scenario: The catalogue is named

- **WHEN** the anchor entry is read
- **THEN** it names the marker facts file that holds the pattern catalogue and the marker knowledge

### Requirement: Hotspot entry states what the join measures

The entry for the hotspot analysis SHALL state that the hotspot is the join of the change frequency of a class and its
complexity, that the change frequency counts the commits of the counted window of the history, and that the class of a
file carries the change count of that file.

#### Scenario: The join is named

- **WHEN** the hotspot entry is read
- **THEN** it names the two figures the hotspot joins, and states that neither figure alone makes a hotspot

#### Scenario: The bound of the history is named

- **WHEN** the hotspot entry is read
- **THEN** it names the counted window of the history as the bound of the change frequency, and states that a
  truncated history leaves the classes changed before it out of the ranking

### Requirement: Strategy entry states what the cost is made of

The entry for the modernisation strategy analysis SHALL state which counted figures the cost of a modernisation area is
made of, and SHALL state that the order of the areas follows that cost and is not a decision.

#### Scenario: The figures of the cost are named

- **WHEN** the strategy entry is read
- **THEN** it names the figures the cost rests on, among them the coupling of the module, the price of the cut of the
  module and the test coverage of the area

#### Scenario: The order is not a decision

- **WHEN** the strategy entry is read
- **THEN** it states that the order of the areas follows the counted cost, that the analysis names no area to start
  with, and that the business value of a change is not part of the analysis
