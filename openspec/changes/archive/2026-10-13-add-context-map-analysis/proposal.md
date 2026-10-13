# Proposal

## Why

Change `2026-10-13-add-domain-vocabulary-analysis` measures the language of a single module. The boundary question is relational and stays unanswerable there: two modules can be structurally independent and still speak each other's language, which is a domain leak, and two modules can be tightly coupled while sharing no vocabulary at all, which is a translation layer doing its job. The engine already holds both sides of that comparison — the counted vocabulary per module and the inter-module import matrix — but never joins them.

## What Changes

- Aggregate the per-module vocabulary into a project-level view: one row per term and module, plus a rollup naming for each term how many modules use it and which ones.
- Compute the pairwise lexical overlap of the modules, naming the shared terms and the terms only one side uses.
- Detect foreign domain terms: a term that another module establishes, appearing in this module, reported with its owning module and its share of this module's domain vocabulary.
- Join lexical coupling with the existing inter-module import matrix into a structural/lexical quadrant per module pair, so a domain leak and a translation layer become distinguishable.
- Write the evidence as CSV files, one row per fact, at project level.
- Add a context map evaluation task that proposes bounded context candidates and the relation between them, and render its result as a documentation chapter with the quadrant.
- Modify `report-directory-structure`: a project-level report that carries several views names each file after its view, beside the existing module-name and report-name rules.

## Capabilities

### New Capabilities

- `context-map-analysis`: the project-level aggregation of the vocabulary evidence, the overlap and intrusion measurement, the structural/lexical quadrant, and the context map judgement derived from them.

### Modified Capabilities

- `analysis-reference`: the documented set of analyses gains the context map analysis, so the reference holds its entry with the quadrant and the intrusion measure explained.
- `documentation-coverage`: the report renders the context map section with its quadrant table, carrying the pointer to its reference entry.
- `report-directory-structure`: a project-level report with several views names each view file after the view, so that views whose rows are not interchangeable do not have to share one file or one directory each.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/`: a project-level overview tool that reuses the tokenization and counting of the vocabulary analysis and joins it with the inter-module dependency report.
- New project-level CSV report directory `DomainLanguageOverview/` with one file per view, and rows in the tens of thousands for a large project.
- `analysis/software-architecture/`: one new task, prompt, response schema, documentation chapter and reference entry, and the README task table.
- Depends on the vocabulary analysis being in place; without its counted evidence there is nothing to aggregate.
