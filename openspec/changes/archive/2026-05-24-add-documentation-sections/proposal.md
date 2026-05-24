## Why

The `Documentation.md.hbs` template currently has sections for cyclomatic complexity and nesting depth analysis results, but lacks sections for method visibility analysis, class inheritance analysis, and method complexity analysis — all of which were implemented in a previous change and have active `tasks.yaml` entries producing evaluation data. Without template sections, these evaluations exist in the analysis state but are never rendered in the generated documentation.

## What Changes

- Add 3 new sections to `analysis/software-architecture/documentation/Documentation.md.hbs` for rendering visibility, inheritance, and method complexity evaluation results per module
- Each section follows the existing pattern: module-loop → evaluation table with aspect/urgency/criticality/expectation/reasoning/finding/recommendation columns
- Response properties used: `visibilityEvaluation`, `inheritanceEvaluation`, `methodComplexityEvaluation`

## Capabilities

### New Capabilities

- `documentation-coverage`: Ensures all active analysis evaluation properties have corresponding sections in the documentation template.

### Modified Capabilities

- *(None — existing capabilities unchanged)*

## Impact

- **Documentation**: `Documentation.md.hbs` — add 3 sections (~45 lines)
- **No code changes** — only template changes
- **No new dependencies**