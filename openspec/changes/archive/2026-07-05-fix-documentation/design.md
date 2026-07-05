## Context

The documentation template `Documentation.md.hbs` renders evaluation results from batch analysis tasks (cyclomatic complexity, nesting depth, visibility, etc.) in per-module tables. Each table expects `evaluations[]` items to be JSON objects with `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation`.

In practice, the LLM produces `evaluations[]` as plain strings for 16 of 17 evaluation tasks, and as partial objects (only `finding` key) for 1 task. A workaround was added to the template to handle these malformed formats, but this is brittle — it produces tables with mostly dashes and hides the root cause.

The correct fix is at the source: tighten the prompts so the LLM always produces the correct object format, then simplify the template to only handle that format.

## Goals / Non-Goals

**Goals:**
- All 17 batch evaluation prompts explicitly require `evaluations[]` items as JSON objects with all required fields
- Template `Documentation.md.hbs` has no fallback branches for strings or partial objects
- Regenerated documentation shows all table columns filled correctly

**Non-Goals:**
- No changes to `ModuleBatchEvaluation.json` schema — it already defines the correct format
- No Java code changes
- No changes to task YAML definitions
- No re-running of analysis tasks (existing data stays as-is until next analysis run)

## Decisions

1. **Fix prompts, not schema** — The JSON Schema already requires the object format. The issue is the LLM ignores it. Adding explicit instructions in the prompt is the most direct fix with zero code changes.

2. **Single line added to all 17 prompts** — Each `*_evaluation_all.md` prompt gets the same additional bullet in its Response Requirements section. This ensures consistency and is easy to maintain.

3. **Template reverts to single table row** — Remove the `{{#if aspect}}` / `{{#if finding}}` fallback branches. The template iterates `evaluations[]` and renders one table row per item, assuming all fields exist. If a future task produces malformed data, the table will show empty cells — making the problem immediately visible rather than silently producing dashes.

4. **No migration of existing data** — Existing `analysis.json` data with string evaluations stays as-is. The fix takes effect on the next analysis run. The template change is applied now but only matters when data is correct.

## Risks / Trade-offs

- **Existing data still broken** — Until the next analysis run, regenerated docs will show empty columns for string evaluations. Acceptable: the fix is in the prompts, and re-running analysis is the natural next step.
- **LLM may still produce strings** — Prompt engineering is not guaranteed. If the LLM continues to ignore the instruction, the template will show empty cells instead of dashes. This is intentional — it makes failures visible. Mitigation: monitor first analysis run after fix, tighten prompt further if needed.
- **No validation layer** — The system currently only warns on schema violations, it doesn't reject malformed output. Adding rejection logic would be a separate change.
