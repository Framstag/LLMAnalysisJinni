# Proposal

## Why

The architecture analysis now produces two outputs whose meaning is nowhere stated: the per-module dependency diagrams (`ClassDependencyGraphEvaluationAll`, task table row 37) and the god class ranking (`GodClassEvaluationAll`, row 38). The analysis README documents the tasks but not what these two artefacts mean, so a reader cannot tell what a diagram omits, why a group was not drawn, or why a class ranked first without that being a verdict. The configuration properties that drive both - the diagram budgets and the god class ranking limits - are seeded into every workspace `config.json` with no explanation of what changing them does.

The knowledge exists and is consistent: the diagram semantics are specified in `dependency-diagram` and implemented in `DependencyDiagrams`, the ranking factors in `god-class-detection` and `GodClassRanking`. Nothing has to be decided here, only written down where the reader of the analysis looks.

## What Changes

- Document the dependency diagrams in the analysis README: the two levels per module, that groups come from the cheapest separation, that collapsed groups are weighted with the total coupling to them, that every omission is stated in the caption, and that a diagram which could not be drawn is reported instead of stored broken.
- Document the diagram budgets and what each one does, with the defaults the workspace configuration is seeded with.
- Document the god class definition: the factors that are scored, that every factor is a percentile within the module rather than an absolute threshold, that no pass or fail verdict is produced, that cohesion is a TCC-like approximation and not TCC, and that a large cohesive class is not a candidate.
- Document the two god class ranking limits and their defaults.
- Require the task table of the analysis README to be a single Markdown table. Four of its rows are separated by blank lines, so the table currently renders as four tables.
- Bring the directory structure section of the analysis README up to date. It still names the documentation template `Documentation.md.hbs`, while the directory holds `Documentation.adoc.hbs`.

No task, prompt, schema, tool, or emitted output changes. This is documentation of what the pipeline already does.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `analysis-documentation`: the capability already requires the analysis README to document the purpose and the task table. It gains requirements that the README also documents the semantics of the dependency diagram output, the semantics of the god class ranking, the configuration properties that drive both, that its task table is one Markdown table, and that its directory listing names the files that are there. Requirements are ADDED; no existing requirement changes.

## Impact

- `analysis/software-architecture/README.md` - new sections and two corrections to existing content (task table, directory structure); the existing task table content and its quality ratings are otherwise untouched.
- No source, template, schema, prompt, task YAML, build, or dependency change.
- The referenced behaviour is specified in `dependency-diagram` and `god-class-detection`; this change documents it for the analysis reader and deliberately does not restate those specs' requirements as new engine behaviour.
