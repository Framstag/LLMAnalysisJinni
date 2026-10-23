# Proposal

## Why

The modernisation anchor analysis cannot complete on a real project. On the workspace `workspaces/maven` its prompt
grew to 1,416,326 tokens against a model context of 1,048,576, so the step failed and the strategy analysis that
rests on it never ran. The cause is that the anchor prompt calls eight tools whose results are the whole project: the
seams of 82 modules, 6,800 entry-point rows, the split ladders, the dependency matrix and the coverage reports. The
capability that was specified to read bounded evidence reads the project.

The same run showed a second defect in the rendered report: the hotspot ranking reports one row per module, and a
project whose aggregator module holds the same classes as its inner modules shows the same class three or four times
in the top ranks, so the chapter's first rows are one class repeated instead of the three places that changed most.

## What Changes

- Add a tool that pre-aggregates the anchor candidates of the project per anchor type and returns a bounded, ranked
  list per type, with the total count of the candidates of that type stated beside the bounded list, and with the
  budget that bounds it as a workspace property.
- Let the anchor prompt read that one bounded payload, the context map result and the marker facts, and stop calling
  the eight project wide tools itself.
- Report a hotspot once per class, naming the modules the class appears in and the highest complexity of its
  appearances, instead of once per module.
- Name the new budget in the analysis reference entry of the modernisation anchors, with the value a workspace is
  seeded with.

## Capabilities

### Modified Capabilities

- `modernisation-anchor-detection`: the candidates of the anchor analysis are bounded per anchor type by a budget, and
  the anchor analysis reads a bounded candidate payload instead of the counted evidence of the whole project.
- `hotspot-analysis`: a class is reported once, with the modules it appears in, instead of once per module.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/`: one new tool that aggregates and bounds the anchor candidates, and a
  change to the hotspot ranking that folds the appearances of a class into one row.
- `src/main/java/com/framstag/llmaj/config/Config.java`: one new workspace property with its seeded value.
- `analysis/software-architecture/`: the anchor prompt, its task tool whitelist and its reference entry.
- No report format change. The counted evidence, the report directory layout and the existing metrics are unchanged.
