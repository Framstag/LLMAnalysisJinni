# Proposal

## Why

The analysis reads the code as it is right now. It cannot say which part of the project is changing, which part has
not been touched in years, which part only one person knows, or which files always move together. Every judgement
about modernising a system needs that: a module that is never changed is not worth a migration, a file that changes
with another file belongs to the same unit of work even when the build says otherwise, and a module whose authorship
is concentrated in one person is a risk that no static metric shows.

## What Changes

- Add a tool that reads the version history of the project and counts, for every file, how often it changed, how many
  lines were added and removed, when it changed first and last, and who changed it.
- Aggregate that history per build module: the files that changed, the churn of the module, how recent its last
  change is, and how the authorship of the module is spread over its authors.
- Count the files that change together, so that the unit of work the history shows can be told apart from the unit
  the build file declares.
- Report a project whose history cannot be read as a result with its reason, so that a project that is not a
  repository, a machine without a git executable or a shallow clone yields an analysis that says what is missing
  instead of a failed run.
- Bound the history that is counted by a window, name that window in the result, and state what the bound leaves out.
- Write the counted history as project level CSV reports, one file per view.

## Capabilities

### New Capabilities

- `version-history-analysis`: the counted version history of the project, the aggregation of that history per build
  module, the files that change together, and the result that reports a history that cannot be read.

### Impact

- `src/main/java/com/framstag/llmaj/tools/`: one new tool that reads the version history of the project directory and
  writes its evidence. It reads the history with the git executable of the machine and adds no dependency to the
  build.
- New project level CSV report directories for the counted views of the history.
- `analysis/software-architecture/`: no task, no prompt and no schema in this change. The history is counted
  evidence that later changes read.
- The analysis runs on a project directory that is a git repository. A project that is not one is a supported case:
  the tool reports the absence and the analysis that reads it states that the history is absent.
