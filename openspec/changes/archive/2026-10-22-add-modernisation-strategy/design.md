# Design

## Context

See proposal.md for the motivation. What shapes the approach:

- The anchor analysis produces a ranked list of anchors with their evidence and their candidate patterns. The hotspot
  analysis produces a ranked list of classes with two figures. Both live in the analysis state of a run.
- The counted evidence the cost of a change rests on already exists: the afferent and efferent coupling of a module
  and the dependency matrix, the strength of the reference edge that a cut would sever, the test coverage per module,
  the number of files of a module.
- The pattern catalogue lives in the marker facts of the analysis, together with the marker knowledge.
- The report separates the metric evaluations from the project level judgements. The strategy is a project level
  judgement.
- The last four changes of the project fixed oversized per-module prompts. The size of an input is a concern the
  project defends against.

## Goals / Non-Goals

**Goals:**

- One modernisation area per candidate, with the patterns that fit it and the evidence for and against each of them.
- A cost per area that rests on counted figures, with the figures named beside it.
- An order that the reader can reorder, because the figures are reported next to it.
- An input size that does not grow with the number of findings of the project.

**Non-Goals:**

- Choosing. The analysis names the patterns that fit and the price of each; the reader picks.
- A roadmap with dates, owners or milestones. The analysis has no knowledge of the organisation.
- The business value of a change. The analysis ranks by the counted cost and says so.
- A new metric. The cost rests on the counted evidence that exists.

## Decisions

### The area is the unit, not the anchor and not the pattern

An area holds one place, the patterns that fit it, the evidence for and against each of them and the cost of the
change. Splitting the areas from the patterns keeps the comparison in one place: the reader sees the price of every
pattern for that place at once, which is exactly what the anchor analysis could not show.

*Alternative considered:* a flat list of anchor and pattern pairs. Rejected: the pairs of one anchor would be spread
over the list, and the comparison the reader wants would be lost.

### The cost rests on counted figures and the figures are named

The cost of an area rests on the coupling of the module, the price of the cut and the test coverage of the area. The
result names the figures beside the cost, and it says which figure is missing when the counted evidence does not hold
it.

*Alternative considered:* one cost score. Rejected: a single number hides the weighting, and the project states the
figures rather than the score for the same reason the god class ranking reports its percentiles.

### The order is a cost order and the analysis names no area to start with

The areas are ordered by the counted cost, and the result states that the choice is the reader's. The pattern of the
project is that an analysis reports what it measured and lets the reader judge; a modernisation strategy that named
the first area would be a plan the analysis cannot support.

*Alternative considered:* name the area with the best ratio of value to cost. Rejected: the analysis holds no value
figure, and a ratio of two numbers of which one does not exist is a made up number.

### The patterns come from the catalogue in the marker facts

The pattern catalogue of the anchor change is the single place the patterns live, and the strategy analysis takes them
from there. A pattern that the catalogue does not hold is not named.

*Alternative considered:* let the model name any pattern it knows. Rejected: the same project would be reported with
a different set of patterns on every run, and the reader could not tell a considered pattern from an invented one.

### The strategy analysis reads the anchor result, the hotspot result and the counted evidence, and no prose findings

The inputs are bounded: the anchor result is a list of anchors, the hotspot result is a list of ranked classes, and the
counted evidence is a number per module. The prose findings of the other evaluation analyses are not read, for the
same reason the anchor analysis does not read them: they are the largest part of the analysis state and they are the
opinion of an earlier call about a single metric.

*Alternative considered:* read the findings of the other evaluations to catch what the counted evidence misses.
Rejected: the size of the input would grow with the number of findings, which is the failure the last four changes
removed from the per-module prompts.

### An area type whose input is missing is reported as absent

The areas that rest on the hotspots are absent when the hotspot analysis did not run, and the result says why. The
SBOM absence is the precedent: a missing input is a result, not a failure of the run.

### The response is a project level entity list

The response holds the reasoning and the areas, and each area holds its module, its element, the anchor type it rests
on, its patterns with their evidence and the figures of its cost. It follows the shape of the context map and the
anchor result, both of which are project level entity lists, and not the shape of the module evaluation, which fits a
finding about a module and not a place with several patterns.

## Risks / Trade-offs

- **The cost of an area is only as good as the counted evidence.** A module with no reported coupling has no coupling
  figure, and the cost rests on what is there. → The result names the missing figures, and the reference entry states
  that the cost is a cost of the counted evidence.
- **Two runs may order the areas differently.** The judgement of the model decides which patterns fit and what the
  evidence for and against them is. → The figures and the counted evidence are fixed, and the result reports them, so a
  difference between two runs is visible in the parts that are not judgement.
- **The areas inherit the gaps of the anchor analysis.** An anchor type that rests on a missing input is absent, and
  the areas of that type are absent with it. → The result names the absence and the reason.
- **The strategy analysis adds one model call to a run.** → The task reads bounded inputs, so the prompt does not grow
  with the project.
- **A reader may take the order as a plan.** → The result, the chapter and the reference entry all state that the
  order is the counted cost and that the choice is the reader's.
