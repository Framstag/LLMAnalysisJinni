# Design

## Context

The run on `workspaces/maven` is the evidence:

- The anchor step was rejected with `The prompt is too long: 1416326, model maximum context length: 1048576`. Its
  prompt calls eight tools; their results are project wide: the seams of 82 modules, 6,800 entry point rows, the
  split ladders, the inter-module matrix, the coverage reports, the import distribution and the dependency list.
- The project already has a precedent for a bounded ranking: the god class ranking is bounded by
  `godClass.rankingLimit` and `godClass.batchRankingLimit`, seeded in `Config`, and the analysis reference names the
  seeded value.
- The counts the anchor analysis needs already exist and are cheap to aggregate: the seam tool and the entry point
  tool read the raw module reports, the split candidate ladder and the inter-module matrix exist as reports, the test
  coverage and the import distribution are per module counts.
- The hotspot ranking reports per module. For maven, the aggregator module holds the same classes as its inner
  modules, so the top three ranks were one class three times.

## Goals / Non-Goals

**Goals:**

- An anchor input whose size is bounded by a configuration value and not by the size of the project.
- One row per hotspot class, with the modules it appears in.
- The aggregate over the candidates done in Java, where every number can be checked.

**Non-Goals:**

- A change of the counted evidence, of the report format or of the report directory layout.
- A per-anchor-type task, which would multiply the model calls instead of bounding the input.
- A change of the pattern catalogue or of the anchor types.

## Decisions

### One tool aggregates and bounds the candidates, the model judges

The new tool returns, per anchor type, the strongest candidates up to a budget, each with its evidence and the
analysis that counted it, and the total number of candidates of that type. The model then reasons over a payload whose
size is set by a configuration value.

*Alternative considered:* add a limit parameter to each of the eight tools and let the prompt ask for the top of
each. Rejected: eight calls with eight limits is eight chances to blow the budget, and the model would have to merge
eight rankings it cannot compare.

*Alternative considered:* one task per anchor type. Rejected: seven model calls instead of one, on the task that was
designed to be the cheapest judgement of the pipeline.

*Alternative considered:* let the prompt read the CSV reports of the tools. Rejected: the prompt cannot read files;
the model would call the file tool and the size would be bounded by nothing.

### The budget is a workspace property with a seeded value

One property, `anchor.candidatesPerType`, seeded to 15 in `Config`, in the style of `godClass.rankingLimit`. The
reference entry names it with its seeded value, which the existing test of the reference enforces.

*Alternative considered:* a fixed constant in the tool. Rejected: the size of a prompt that fits a model is a
property of the workspace, exactly like the other budgets.

### The domain anchors keep their own input

The domain anchors rest on the context map result, which is an earlier analysis result and not tool evidence. The
tool therefore covers the anchor types that rest on counted evidence, and the prompt reads the context map result
beside the bounded payload. The context map result is already bounded by `contextMap.maxPairsPerClassification`.

### The hotspot folds the appearances of a class in the tool

The ranking is folded by qualified class name inside the tool, before the rank is assigned, so the rank counts the
places and not the copies. The row names the modules and the highest complexity of the appearances, because a class
in an aggregator module and in its inner module can carry slightly different counted figures and the reader should
see the strongest one.

*Alternative considered:* fold in the template. Rejected: the model would still rank the copies, and the ranks it
reports would be wrong.

## Risks / Trade-offs

- **A bounded list hides a candidate the reader would have wanted.** → The total count is stated beside the list, the
  budget is a workspace property, and the counted evidence stays on disk for a reader who wants more.
- **The tool duplicates aggregation that the other tools already do.** → It reuses them where they are cheap to call
  and aggregates once; the counts it returns are the counts of those analyses.
- **Folding the hotspot by class name loses the per module view.** → The row names the modules, so the view is still
  there; only the duplication is gone.
