## Context

`Documentation.md.hbs` is a Handlebars template rendered during the `document` command. It iterates over modules and evaluation properties (e.g., `cyclomaticComplexityEvaluation`, `nestingDepthEvaluation`, `build_analysis`, `code_size_distribution`) to produce the final analysis report. Three evaluation properties — `visibilityEvaluation`, `inheritanceEvaluation`, `methodComplexityEvaluation` — were added to the pipeline but never wired into the template.

## Goals / Non-Goals

**Goals:**
- Add 3 template sections matching the existing `cyclomaticComplexityEvaluation` pattern
- Each section renders per-module with a table of aspect/urgency/criticality/expectation/reasoning/finding/recommendation

**Non-Goals:**
- No new evaluation properties or pipeline changes
- No code changes
- No spec changes

## Decisions

### Decision 1: Match existing pattern exactly

**Chosen**: Each new section is a direct copy of the nesting depth section pattern, with only the response property name changed. This keeps the template consistent and predictable.

### Decision 2: Order follows analysis pipeline

**Chosen**: New sections placed after the existing complexity/nesting depth sections, in pipeline execution order: visibility → inheritance → complexity.