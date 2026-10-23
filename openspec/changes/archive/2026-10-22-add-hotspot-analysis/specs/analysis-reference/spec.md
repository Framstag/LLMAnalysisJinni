# Spec Delta

## MODIFIED Requirements

### Requirement: One entry per documented analysis

The reference document SHALL hold one entry per analysis, and SHALL hold an entry for each of the java metric evaluations, the inter-module dependency analysis, the class reference structure and split candidates, the god class ranking, the dependency diagrams, the domain vocabulary and domain language evaluations, the context map analysis, the modernisation anchor analysis and the hotspot analysis.

This delta is written against the requirement as it reads after the change `add-modernisation-anchors` has been archived, because that change adds the modernisation anchor analysis to the same enumeration.

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
