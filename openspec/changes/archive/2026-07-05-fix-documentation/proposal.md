## Why

The "Analysis of X per Module" sections in the generated documentation have empty table columns because the LLM produces `evaluations[]` as arrays of plain strings instead of JSON objects with `aspect`, `urgency`, `criticality`, `expectation`, `reasoning`, `finding`, `recommendation` fields. The template iterates expecting objects, so all columns except module-level `reasoning` render empty.

A previous workaround added fallback handling for string evaluations in the template, but this is brittle — it hides the data quality problem and produces tables with mostly dashes. The correct fix is to enforce the object format at the source (the LLM prompts) and keep the template clean.

## What Changes

- Update all 17 `*_evaluation_all.md` prompts to explicitly require `evaluations[]` items as JSON objects with all required fields
- Simplify the documentation template `Documentation.md.hbs` to remove string/partial-object fallback handling — only accept the correct object format
- Regenerate documentation to verify all columns render correctly

## Capabilities

### New Capabilities
- `enforce-evaluation-object-format`: Ensures LLM output for batch evaluation tasks always uses proper JSON objects for `evaluations[]` items, not plain strings

### Modified Capabilities
- `documentation-template`: Remove fallback branches for malformed evaluation data; template only handles the correct object schema

## Impact

- 17 prompt files in `analysis/software-architecture/prompts/` — minor wording addition
- 1 template file `analysis/software-architecture/documentation/Documentation.md.hbs` — remove `{{#if aspect}}` / `{{#if finding}}` fallback branches, revert to single table row
- No Java code changes needed
- No schema changes — `ModuleBatchEvaluation.json` already defines the correct object format
