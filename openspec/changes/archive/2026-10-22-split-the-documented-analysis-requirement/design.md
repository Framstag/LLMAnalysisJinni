# Design

## Context

See proposal.md for the motivation.

The requirement of the analysis reference that holds the documented set of the analyses states one behaviour: every
analysis of the report has exactly one entry in the reference. The enumeration of the analyses is a list that grows
with every analysis the report renders, and it was written into the text of the requirement. The text of a
requirement may not exceed 500 characters, and the strict validation of the specifications fails on a requirement that
does, so the CI gate cannot be green while the enumeration sits there.

## Goals / Non-Goals

**Goals:**

- A requirement that states one behaviour and stays inside the length a requirement may have.
- The enumeration complete: no analysis of the report is left out of the documented set.
- The scenarios of the requirement untouched.

**Non-Goals:**

- A change of the reference document, of the report or of the test that checks them. The requirement describes what
  the reference holds, and the reference holds what it held.
- A different wording of the behaviour. The requirement keeps its name and its behaviour.

## Decisions

### The enumeration moves into a scenario of the requirement

The behaviour of the requirement is that the reference holds one entry per analysis of the documented set. The
enumeration says which analyses those are, and a list belongs in a scenario, where the examples and the lists of the
project already live. The scenarios of the requirement are kept whole, and one scenario is added that names the
analyses of the documented set.

*Alternative considered:* shorten the enumeration by naming the analyses less precisely. Rejected: the documented set
is what tells a reader which analyses the reference covers, and a vague enumeration would let an analysis slip out of
the set without anybody noticing.

*Alternative considered:* split the requirement into two, one for the behaviour and one for the enumeration. Rejected:
the enumeration is not a behaviour of the reference, it is the content of a set, and a second requirement would
describe a list.

### The change is a change of its own

The rule of the project is that an existing requirement is not split or trimmed inside a change that is made for
something else. This change is made for the split, so it is a change of its own, and the changes that add an
analysis to the documented set are written against the state that this change leaves behind.

## Risks / Trade-offs

- **A change that adds an analysis to the documented set has to know the state of the requirement.** → The changes
  that follow this one are written against the requirement as this change leaves it.
- **The enumeration is now in a scenario, and a scenario reads as an example.** → The scenario states what the
  documented set names, and the other scenarios of the requirement refer to that set.
