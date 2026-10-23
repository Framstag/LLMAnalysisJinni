# Proposal

## Why

The anchor analysis wastes its budget on the same element several times. A project whose aggregator module holds the
same classes as its inner modules is counted once per module report, so the top of the entry point list and the top of
the seam list are three or four appearances of one class or one interface and the ranks below them are pushed out of
the bounded list. In the run on `workspaces/maven` the seam list held `InputLocationTracker` at ranks 1, 2 and 3 for
`Apache_Maven`, `maven-api-model` and `api`, and the entry point list held two JMH benchmark classes at ranks 1 to 6.

The entry point list has a second defect of the same kind: a benchmark harness or a test class that declares a `main`
method is counted as the strongest entry point of the project, while a production entry point ranks below it, so the
budget is spent on places a modernisation cannot attach to.

## What Changes

- Report an anchor candidate once per element, naming the module the element is read in and the other modules whose
  reports hold it, instead of reporting the element once per module report.
- Rank a production entry point above a test or generated entry point, so that the budget of the list is spent on the
  places a caller reaches the product through.
- Keep the counting of the underlying analyses unchanged: the entry point analysis and the seam analysis keep
  reporting per module, and the fold happens in the anchor candidate tool.

## Capabilities

### Modified Capabilities

- `modernisation-anchor-detection`: a candidate is reported once even when several module reports hold the same
  element, and the entry point candidates of production code rank above the ones of test or generated code.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/AnchorCandidateTool.java` and the candidate record: the fold and the
  order of the entry point candidates. The payload gains the modules a candidate was seen in.
- `analysis/software-architecture/prompts/modernisation_anchors.md` and the strategy prompt: they name the modules a
  candidate was seen in.
- No change to the entry point analysis, the seam analysis, the report format or the CSV reports.
