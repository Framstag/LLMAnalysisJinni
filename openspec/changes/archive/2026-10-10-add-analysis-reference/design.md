# Design

## Context

See `proposal.md` - Why. The constraints that shape the approach:

- `DocumentCmd` compiles `Documentation.adoc` through a `FileTemplateLoader` whose root is
  `<analysis>/documentation` and whose suffix is `.hbs`. jknack appends the suffix when a partial name does not
  already end with it, which is why the template itself is `Documentation.adoc.hbs`. A partial named
  `AnalysisReference.adoc` therefore resolves to `AnalysisReference.adoc.hbs`, and no Java change is needed to
  include a file from that directory.
- `AnalyseCmd` uses a second loader, rooted at the analysis directory with an empty suffix. The prompts reach
  static text as `{{> facts/<file>.md}}`. The two roots do not overlap, so a file shared between a prompt and
  the report template would need either a `../` path (unverified with this loader) or a change to one of the
  roots.
- Every analysis tool writes raw output as well: histograms as `Distribution` values, CSVs under
  `FieldCountPerClass/`, `FieldToMethodRatio/`, `InterModuleDependencyMatrix.csv` and elsewhere. The report
  renders only the model's evaluation of those numbers, never the numbers themselves.
- Thresholds and interpretation rules exist today in 18 `prompts/module_*_evaluation.md` files. No task in
  `tasks.yaml` and no spec references them; no spec states the interpretation rules of a java metric except
  `inter-module-dependency-analysis` and `god-class-detection`.
- The report shows no per-run content that this change can rely on: a section renders only when its result
  property exists in the state.

## Goals / Non-Goals

**Goals:**

- One authored document that states, per analysis, what is measured, how the number is to be read, what a
  weight weighs and what the metric cannot see.
- That document reaches the reader of the report, and the reader reaches it from the section they are reading.
- One place states each number: after this change, a threshold or a default appears once.
- The document is testable without generating a report from a live analysis run.

**Non-Goals:**

- Changing any metric, threshold, budget or default. The values are migrated as they are, presented as the
  project's working heuristics rather than as reviewed standards.
- Feeding the reference to the model. The batch prompts stay as they are, so no token is spent on the reference
  and the evaluation text of a run can still disagree with it (see Risks).
- Documenting the analyses that render self-explanatory numbers: project summary, SBOM and dependencies,
  licenses, technology stack, module overviews, purpose, directory structure, architecture per module, build
  file analysis, file statistics and code size distribution keep their present treatment.
- Rendering the raw numbers, the histograms or the CSVs in the report.

## Decisions

### One central reference document, one chapter, pointer lines

The reference is a single file with one entry per analysis. The report includes it once as a chapter and each
of the 21 documented sections carries a pointer line naming its entry.

Alternatives: an interpretation block inline under each of the 21 sections (puts text next to numbers, but
repeats 21 includes and grows the report by a block per section); one file per analysis in the report directory
(cheap per-section include, but 21 files to open before the whole picture is visible, and it contradicts the
preference for one parallel document); a chapter plus inline blocks (costs text and include lines twice).

Chosen because the reader gets both routes: the section names the entry, the chapter reads as one coherent
methodology text.

### The document lives under `documentation/`, not beside the README

`documentation/AnalysisReference.adoc.hbs` is reachable by the reporting loader with a one-line include.
Placing it beside `README.md` would put it in a directory the reporting loader cannot see, and would cost a
change to the loader root plus a new engine test, for a file the report consumes anyway.

Cost of the choice: the human-facing file carries a `.hbs` suffix and sits next to the template rather than
beside the README. This is the same shape the template already has, so no new oddity is introduced.

### Static text only, no model content and no per-run numbers

The reference states metrics, not runs. This is what makes it safe to author once, include unconditionally and
assert in a unit test, and it matches how the dependency diagrams are treated: the engine emits structure, the
model never authors it. A reader who wants the numbers of a run gets them from the section the entry explains.

### Prompts stay unchanged

Including the reference in the batch prompts would put all 21 entries into each of the 21 calls, whether or not
that call needs them, on models such as `qwen3:4b` and `gpt-oss:20b`; including only the relevant entry would
require the knowledge to be addressable per analysis, which the single-file decision gives up. The alignment
goal is therefore deferred, and the report states the metric rules independently of what the model said in a
run.

### The 18 unreferenced prompts are deleted in this change

Their thresholds are the raw material of the entries. Leaving them behind would create a second, drifting copy
of the same judgement in a place nothing reads. Verification that the deletion is safe: no task in
`tasks.yaml` names them, and no spec references them (only two archived change directories do).

### The README loses its two deep-dive sections

`analysis/software-architecture/README.md` documents the dependency diagrams and the god class ranking in its
own words today. With the reference in place that prose is a duplicate, so it moves into the reference entries
and the README keeps a pointer. The README keeps the task table, the quality key and the directory listing,
which are about the tasks, not about the metrics.

### Steering table with a `Source` column, guarded by a test

Only two analyses have `config.json` properties (`diagram.*`, `godClass.*`); the other 19 are steered by fixed
values. One table with a `Source` column shows both kinds and tells a reader what they can turn. Because a
seeded default would then be stated in the README's replacement, in the reference and in the configuration,
a unit test asserts that the values named in the steering tables equal the values the engine seeds.

## Risks / Trade-offs

- [Thresholds become normative by being written down] → Migrate them verbatim and word each one as a working
  heuristic that the reader may dispute, rather than as a reviewed standard. Any threshold worth changing gets
  its own change.
- [The reference states rules the run's evaluation text may contradict] → The pointer line and the chapter
  both present the reference as the rule, so a contradicting evaluation reads as the model's deviation. Worth
  revisiting if the drift becomes frequent.
- [A configuration default changes and the reference keeps the old number] → The guard test comparing the
  steering tables with the seeded values fails and names the row.
- [A new analysis is added and no entry is written] → The completeness test requires one entry per documented
  analysis and fails on a missing entry; the section-level pointer test fails on a missing pointer line.
- [Report grows] → One chapter of 21 short entries, plus one line per section. Accepted.
- [Deleting the prompts loses a fallback] → The files are in version control, and their content is captured in
  the reference before deletion.

## Migration Plan

1. Author the reference document with all 21 entries and the steering tables.
2. Add the chapter include and the pointer lines to `Documentation.adoc.hbs`.
3. Migrate the thresholds out of the 18 prompts, then delete them.
4. Replace the README deep-dive sections with the pointer and add the reference to the directory listing.
5. Run `mvn verify`. Existing workspaces need no migration: `document` regenerates the report from the current
   templates, and a document generated before this change simply has no chapter.

Rollback is a revert of the change; nothing in the state format, the configuration or the task graph moves.

## Open Questions

- Where in the report the chapter sits: as the last chapter, or directly after the title so a reader meets the
  methodology before the first number. Both satisfy the specs; the tasks place it last and the choice can move
  at review time without touching an entry.
