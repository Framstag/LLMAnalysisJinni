## Why

`analyse -o <task ids> <workspace directory>` - the form documented in `AGENTS.md` - fails with `Missing required parameter: '<workingDirectory>'`. The `-o` / `--executeOnly` option is declared with `arity = "1..*"`, so it keeps consuming every following argument, including the workspace directory positional. The same happens with the comma-separated form documented in `README.md`, and with repeated `-o` occurrences: only `analyse <workspace directory> -o <task id>` works today.

This was found while verifying an unrelated fix: a run that should have executed one task instead aborted before doing anything, and the error message points at the workspace argument rather than at the option that actually swallowed it.

## What Changes

- **`-o` / `--executeOnly` stops consuming the workspace directory**: the option takes a single value whose value may still be a comma-separated list, and it may be repeated. Both `analyse -o TaskId <workspace>` and `analyse <workspace> -o TaskId` work.
- **Existing documented forms keep working**: `-o A,B` and `-o A -o B` remain valid ways to select several tasks.
- **Space-separated ids after the option are no longer accepted as two ids**: `analyse <workspace> -o A B` currently treats `A` and `B` as two ids and will instead take `B` as the workspace directory, so the run fails while loading a configuration that does not exist. This form is not documented anywhere and it is the very greediness that breaks the documented forms, so it is dropped.
- **Documentation corrected**: the usage example in `AGENTS.md` and the option description in `README.md` describe the accepted forms.

Non-goals: no change to how selected tasks are resolved or validated, no change to the `state drop` positional task list, no change to task execution or state handling.

## Capabilities

### New Capabilities
- `execute-only-task-selection`: the command line contract for restricting an analysis run to a named set of tasks, independent of where the workspace directory argument appears.

### Modified Capabilities
- *(none - no existing spec describes this option; the closest CLI-adjacent spec is `parallel-task-execution`, which covers task scheduling and `--single-step`, not task selection)*

## Impact

- **Modified files**: `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java` (option declaration), `AGENTS.md` and `README.md` (documented usage).
- **Tests**: new unit tests in `src/test/java/com/framstag/llmaj/cli/AnalyseCmdOptionParsingTest.java` covering option/positional ordering and both list forms.
- **CLI behaviour**: invocations that passed the workspace directory after `-o` were previously rejected and now work. The only invocation that stops being accepted is the undocumented space-separated id list, where the second value is now taken as the workspace directory.
- **No API, dependency, or configuration format changes.**
