# Proposal

## Why

The generated architecture report shows metric numbers that a reader cannot interpret. Of the 21 evaluation
sections it renders, 17 carry no live statement of what the metric measures, whether a high or a low value
is the problem, what its weights mean, or where its thresholds come from. The only statements that exist
today live in 18 `prompts/module_*_evaluation.md` files that no task in `tasks.yaml` and no spec references
any more. A reader therefore cannot tell a module-relative percentile from an absolute count, an edge
weight from an import count, or an approximation such as the cohesion factor from the exact metric.

## What Changes

- Add one central reference document, `analysis/software-architecture/documentation/AnalysisReference.adoc.hbs`,
  holding one entry per relevant analysis: what is measured, how to read the result, what the weights mean,
  and the caveats of the metric.
- Render that reference once as a chapter of the generated report, and add a pointer line under each of the
  21 sections naming the entry that explains it.
- Give every entry a steering table that names the values which steer the analysis and where each of them comes
  from: a `config.json` property with its default, or a fixed value of the analysis knowledge.
- Migrate the thresholds and interpretation rules out of the 18 unreferenced per-module evaluation prompts into
  the reference, then delete those 18 files, so that one document states each number.
- Move the dependency diagram and god class ranking prose out of the analysis README into the reference, which
  becomes the single place that explains an analysis, and leave the README pointing at it.
- Name the new document in the README directory listing.
- No Java change: the documentation template loader already reaches `documentation/`, and the reference is
  included as a partial.

## Capabilities

### New Capabilities
- `analysis-reference`: one reference document describing what each analysis measures, how its result is to be
  read, what its weights mean and what its caveats are; its single-source role for the report and the README;
  its rendering as a chapter and the pointer line each documented section carries.

### Modified Capabilities
- `documentation-coverage`: the coverage rule extends from "every evaluation result has a rendering section" to
  "every rendered evaluation section names the reference entry that explains it".
- `analysis-documentation`: the README no longer documents the dependency diagrams and the god class ranking in
  its own words but points to the reference, and its directory listing names the reference document.

## Impact

- `analysis/software-architecture/documentation/AnalysisReference.adoc.hbs` (new, the reference) and
  `Documentation.adoc.hbs` (the chapter include plus 21 pointer lines).
- 17 files removed under `analysis/software-architecture/prompts/`: the per-module evaluation prompts that no
  task in `tasks.yaml` referenced. `module_analysis_reports.md` stays, because it carries neither a threshold
  nor an interpretation rule and a test pins its wording. No task definition, prompt resolution or analysis run
  changes behaviour.
- `analysis/software-architecture/README.md` loses its two deep-dive sections in favour of a pointer.
- `src/main/` is untouched: no new engine behaviour and no new configuration property. Under `src/test/` four
  files change: `AnalysisReferenceTest` (new) and `DocumentationTemplateTest` cover the reference and the pointer
  lines, `SoftwareArchitectureTaskConfigTest` asserts the README points at the reference, and one line in
  `FileToolTest` follows the new test file, because that test pins the recursive listing of the test tree.
- Readers of `Documentation.adoc` see the analysis methods chapter plus one pointer line per section; existing
  runs keep working, and a document generated before this change simply has no chapter.
