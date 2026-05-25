## Why

The `analysis/software-architecture/` directory contains all task definitions, prompts, schemas, macros, and facts — but has no README. Newcomers (and future agents) cannot quickly understand what analysis is available, in what order tasks run, or what each task evaluates. A README with a task table solves this, and the table format is designed to be easily extended as new analysis steps are added.

## What Changes

- Create `analysis/software-architecture/README.md` with:
  - Purpose of the analysis directory
  - Table of all analysis tasks in execution order
  - Each row: goal, task ID, scope (build tool, programming language, general), implementation quality assessment
- No changes to existing analysis tasks, prompts, or code

## Capabilities

### New Capabilities

- `analysis-documentation`: Documents the purpose, scope, and quality of all analysis tasks in a discoverable README.

### Modified Capabilities

- *(None)*

## Impact

- **New file**: `analysis/software-architecture/README.md`
- **No code changes**: documentation only