# Proposal

## Why

The anchor analysis names the places a modernisation can attach and the candidate patterns that fit each of them, and
the hotspot analysis names the parts of the project that are both complicated and alive. Neither of them compares the
candidates. A reader of the report is left holding a list of anchors, a list of hotspots and a catalogue of patterns,
and still has to work out on their own which pattern fits which place and what the change would cost. The evidence
for that comparison is already there: the blast radius of a module, the coupling of a seam, the price of a cut, the
test safety of an area.

## What Changes

- Add an analysis that reads the anchors, the hotspots, the split candidate ladder, the inter-module dependency
  matrix and the test coverage, and produces one modernisation area per candidate, naming the patterns that fit it,
  the evidence for and against each of them, and what the change would cost.
- Order the areas by the cost the evidence implies, and report the figures the cost rests on beside the order, so that
  a reader who weighs the cost differently can reorder the list without counting anything again.
- State in the result that the order is not a decision: the analysis names what would have to be true for a pattern to
  be the wrong choice, and the reader picks.
- Render the result as a chapter of the generated report and describe it in the analysis reference.

## Capabilities

### New Capabilities

- `modernisation-strategy`: the modernisation areas of a project, the candidate patterns per area, the evidence for
  and against each of them, the cost the evidence implies, and the chapter that renders the result.

### Modified Capabilities

- `analysis-reference`: the documented set of analyses gains the strategy analysis, and the reference gains an entry
  that states what the cost of a change is made of and that the order is not a decision.
- `documentation-coverage`: the report renders the strategy section with the pointer to its reference entry.

## Impact

- `analysis/software-architecture/`: one new task, prompt, response schema, documentation chapter and reference entry,
  and one row in the README task table.
- The analysis reads the results of the anchor analysis and the hotspot analysis, so a run without them has no
  strategy, and the analysis reports the areas it can still support.
- The report gains a chapter. The analysis state gains one property.
