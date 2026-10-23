# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
