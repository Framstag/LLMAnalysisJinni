# Design

## Context

See `proposal.md` - Why. The two artefacts are already specified and implemented, so this design is about where the reader's explanation lives and how it stays true, not about behaviour.

What exists today:

- `dependency-diagram` and `god-class-detection` specify both artefacts as engine behaviour, including the factors, the percentile rule, the caption rule and the budgets.
- `analysis-documentation` specifies the analysis README as the discoverable place for the purpose and the task table of the pipeline.
- `DependencyDiagrams.DiagramSettings.DEFAULT` = `(maxNodes 80, maxOverviewNodes 250, minEdgeWeight 2, maxEdgesPerNode 8, maxGroupDiagrams 12)`, and `Config.seedAnalysisReportBudgets()` writes those plus `MinimumCut.DEFAULT_NODE_LIMIT` = 400 and `GodClassRanking.DEFAULT_RANKING_LIMIT` = 15 / `DEFAULT_BATCH_RANKING_LIMIT` = 5 into every new workspace `config.json`.
- `README.md` already carries the preview guidance for the emitted diagrams; the analysis README row for the diagram task links to it.

Constraint: the analysis README is read by someone interpreting a finished run, not by someone reading the engine's specification. It has to explain what a number or a picture means, and it must not become a second copy of the specs that drifts from them.

## Goals / Non-Goals

**Goals:**

- A reader of the analysis README can interpret a dependency diagram and a god class ranking without reading Java or the specs.
- The configuration properties that shape both artefacts are explained where a reader meets them, with the defaults a workspace actually starts from.
- The explanation stays checkable against the code that produces the values.

**Non-Goals:**

- Changing any emitted output, task, prompt, schema or tool. The specs for `dependency-diagram` and `god-class-detection` stay as they are.
- Restating the engine's requirements in the analysis README. The README explains meaning and configuration; the specs remain the contract.
- Documenting the preview toolchain here. That lives in the root README and the diagram section links to it.
- Adding a test that asserts README prose.

## Decisions

### D1 - Two sections in the analysis README, one per artefact

`### Dependency Diagrams` and `### God Class Ranking`, placed after the task table and before `## Projects Without an SBOM`. Each section answers what the artefact is, how it is bounded, and how to read it, in that order.

Rejected: one combined "Analysis Outputs" section. The two artefacts are read for different reasons and the task table already references them separately; a combined section would mix a picture's semantics with a ranking's semantics and grow into the catch-all this design is trying to avoid.

### D2 - The engine's specification is linked, not restated

Each section states the reader-facing meaning and points at the spec that owns the behaviour. Duplicating requirement text would create a second place to update whenever a factor or a caption rule changes, and the README has no test to catch the drift.

### D3 - Configuration properties are documented with their seeded defaults

The diagram budgets and the ranking limits are given as a table: property, seed value, what it bounds. The values are the ones `Config.seedAnalysisReportBudgets()` writes, so a reader of a workspace `config.json` can match the number there to the description here.

Rejected: listing the properties without values, or pointing at the code. The seed values are the reason the properties are visible at all, and a reader cannot compare a workspace's `config.json` against a table that omits the expected number.

Risk carried with this decision: a changed default makes the table stale. The tasks include a check against `Config` for exactly that reason, and the property names are the stable part.

### D4 - The god class section leads with what is not claimed

The first thing the section says about the ranking is that it is a percentile position within the module with no absolute threshold and no pass or fail verdict, before the factor list. A reader who has just seen "class ranks 1 of 214" reads that sentence first, and the misreading it prevents is the expensive one.

### D5 - Verification is a manual cross-read, not a test

The requirements are about prose a human reads. The verification is a cross-read against the code and the specs: factor names against `GodClassRanking`, budget values against `Config.seedAnalysisReportBudgets()`, level and caption semantics against `dependency-diagram`. Same trade-off as the preview change: not enforced by `mvn verify`, recorded as a task instead.

Alternative rejected: a unit test asserting the README contains given strings. It breaks on rewording and proves nothing about comprehension.

### D6 - The task table is required to be one table

Four rows of the task table are separated by blank lines, so a Markdown reader renders four tables with four header rows. This is a defect in content that the capability already owns, not a formatting preference: the table is how a reader finds a task.

The requirement is stated as a property of the table (no blank line and no other content between two rows) rather than as a fix, so a later row insertion that splits the table again is a violation and not an accident nobody notices.

### D7 - The directory listing is required to name the files that are there

The section still names `Documentation.md.hbs` after the template was renamed to `Documentation.adoc.hbs`, so a reader looks for a file that does not exist while the file they need is one line above.

The requirement is stated as "every file named by name exists", which is checkable by looking, and the rename case is called out as its own scenario because a rename is exactly how this went wrong. The listing deliberately shows example members with `...` for the populated directories, so completeness of the listing is not required, only that what is named is real.

Alternative rejected: generating the listing from the directory. It would put a build step and a generated-file marker into a hand-written README for a five-line tree.

## Risks / Trade-offs

- The README drifts from the code after a factor or budget changes → the table documents property names and seed values from one source (`Config`), the sections link to the specs that own the behaviour, and the task list carries a cross-read for this change.
- The sections grow into a second specification → each section is limited to reader-facing meaning plus configuration; behaviour requirements stay in `dependency-diagram` and `god-class-detection`.
- A reader takes the god class ranking as a defect list → the section opens with the no-verdict statement and names the large-cohesive-class case explicitly.
- Duplicated defaults go stale silently in a future change → accepted and visible: the values are a small table next to the property names, so the check is cheap when a default moves.
- The requirement that named files exist is not machine-checked → accepted; the check is a directory listing against a five-line tree, and the task list carries it for this change. A test that parses a hand-written README would be the brittle kind this design avoids elsewhere.
