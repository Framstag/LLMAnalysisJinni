# Proposal

## Why

The analysis says how complex the code is, but not how alive it is. A class that is complicated and changes every
week is a different thing from a class that is complicated and has not been touched in two years, and a modernisation
decision that cannot tell the two apart is a decision made on half the evidence. The counted version history of the
project exists as of the change that counts it; what is missing is the join between a change and the class that the
change touched.

## What Changes

- Let the class record of the raw Java module report carry the source file the class was declared in, and raise the
  format version of the report, so that a report written before the record carried the source file is regenerated
  instead of being read without it.
- Add a tool that joins the counted version history with the counted complexity of the classes and returns, per
  class, how often the class changed and how complex its methods are.
- Rank the classes by the two figures together, so that the classes that are both complicated and often changed come
  first, and report the two figures beside the rank, so that a reader can disagree with the weighting.
- Add a hotspot evaluation task that judges the counted hotspots, and render the result as a chapter of the generated
  report with the entry in the analysis reference.

## Capabilities

### New Capabilities

- `hotspot-analysis`: the joined count of change frequency and complexity per class, the ranking of the classes by
  the two figures, the evaluation of that ranking and the chapter that renders it.

### Modified Capabilities

- `class-dependency-graph`: the class record of the raw module report carries the source file of the class, so that
  the history of the project can be joined to a class.
- `analysis-reference`: the documented set of analyses gains the hotspot analysis, and the reference gains an entry
  that states what the join measures and what it cannot see.
- `documentation-coverage`: the report renders the hotspot section with the pointer to its reference entry.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/`: the class record gains the source file, the report format version
  rises, and one new tool joins the counted history with the counted complexity. Every raw module report of every
  existing workspace is regenerated once, because its version is behind.
- `src/main/java/com/framstag/llmaj/tools/`: the join reads the counted history of the project.
- `analysis/software-architecture/`: one new task, prompt, response schema, documentation chapter and reference entry,
  and one row in the README task table.
- The change depends on the counted version history. A project whose history is absent has no hotspots, and the
  hotspot analysis reports the absence instead of reporting an empty ranking.
