# Spec Delta

## MODIFIED Requirements

### Requirement: One entry per documented analysis

The reference document SHALL hold one entry per analysis, and SHALL hold an entry for each of the java metric evaluations, the inter-module dependency analysis, the class reference structure and split candidates, the god class ranking, the dependency diagrams, the domain vocabulary and domain language evaluations, the context map analysis and the modernisation anchor analysis.

#### Scenario: All documented analyses have an entry

- **WHEN** the reference document is compared with the evaluation sections of the generated report
- **THEN** each analysis in the documented set has exactly one entry and the set of entries holds no analysis outside it

#### Scenario: Number of entries matches the documented set

- **WHEN** the entries of the reference document are counted
- **THEN** the count equals the number of analyses in the documented set

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
