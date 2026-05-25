## Context

New README for the analysis directory. No code changes.

## Goals / Non-Goals

**Goals:**
- README with purpose statement
- Table of all analysis tasks in execution order
- Columns: goal, task ID, scope, quality assessment
- Easily extendable table format

**Non-Goals:**
- Implementation changes
- Spec changes

## Decisions

### Decision 1: Task order = pipeline execution order

**Chosen**: Sorted by the order tasks appear in `tasks.yaml` and the module analysis loop sequence. Roughly: project-wide → per-module → per-language.

### Decision 2: Quality rating scale

**Chosen**: Simple 3-level scale:

| Rating | Meaning |
|---|---|
| ✅ Stable | Well-tested, used in production |
| ⚡ Beta | Recently added, basic coverage |
| ⏳ Experimental | New, limited testing |

### Decision 3: Table as Markdown, not generated

**Chosen**: Static Markdown table. Easy to hand-edit when adding new tasks. No generation script needed.