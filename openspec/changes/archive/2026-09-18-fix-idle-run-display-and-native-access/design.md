# Design

## Context

See `proposal.md` — Why. Current state, verified in this checkout:

- `DisplayManager` (constructor) calls `ProgressDisplay.addTasks(allTasks)` and only then loops over `preCompletedTaskIds` calling `markTaskPreCompleted()`. `ProgressDisplay.addTasks()` ends with `render()`, so the first frame is painted with every row still `PENDING`.
- `ProgressDisplay` repaints from a scheduled executor every `RENDER_INTERVAL_MS = 500`. A run with nothing to execute finishes in about a millisecond, so no second frame is ever painted; `close()` then prints the completion summary, which lists the tasks as successful.
- `ProgressDisplay.close()` prints `=== Analysis Complete ===` plus one row per task and the aggregate token usage. That is the only part of a finished run that reports outcomes.
- `DisplayDecision` is a pure function of `(executionTrace, stdoutIsTerminal)`; `AnalyseCmd.call()` computes the runnable tasks twice already (`getRunnableTasks()` in the single-step branch and in the dispatch loop) and probes the terminal through `TerminalSupport.detect()` *before* the display is created. `TerminalSupport` probing is itself what loads JLine's FFM provider and prints the four restricted-native-access warnings.
- `TerminalSupport.detect()` never throws; a failure degrades to simple output.
- The jar is built by `maven-shade-plugin` with a `ManifestResourceTransformer` that sets the main class; `maven-jar-plugin` already writes custom manifest entries (`Bundle-License`).
- The unarchived change `fix-cli-config-precedence-and-tui-fallback` already has a delta that MODIFIES the `Non-TTY fallback outputs sequential status lines` and `--execution-trace flag disables TUI` requirements of the same capability. This change must not touch those two requirements, or the two deltas will conflict when they are archived.

## Goals / Non-Goals

**Goals:**

- The first frame a TUI run paints is already correct about tasks that were successful before the run.
- A run that has nothing to execute says so once, starts no TUI, and does not pretend that work happened.
- The TUI does not depend on restricted native access being tolerated by the JVM, and starting the application does not print JVM native-access warnings.

**Non-Goals:**

- Changing what a run with runnable tasks displays, its TUI layout, colours or timing.
- Changing task scheduling, `analysis.json`/`state.json` semantics, retry behaviour, or the `--execution-trace` content.
- Replacing JLine, changing logging, or moving the log level defaults.
- Making the idle case produce a report: the analysis results already live in `analysis.json` and the `document` command.

## Decisions

### D1: Hand the pre-completed set to `addTasks` instead of rendering twice

`ProgressDisplay.addTasks(List<TaskDefinition> tasks, Set<String> preCompletedTaskIds)` applies the successful status while building the rows and renders once at the end. `addTasks(List)` stays as a convenience overload delegating with an empty set, so existing callers and tests keep working.

Alternatives considered:

- *Keep the current order and add an explicit `refresh()` after the marking loop*: the terminal still receives one pending frame per run before the corrected one, which the spec forbids and which flickers on a slow terminal. Rejected.
- *Remove `render()` from `addTasks()` and let the 500 ms timer paint the first frame*: leaves the terminal empty for up to half a second at start, and a very short run would paint nothing at all. Rejected.

### D2: Decide "nothing to execute" in `AnalyseCmd`, before the display exists

`AnalyseCmd.call()` computes the runnable tasks before creating any display and, when there are none *and* no pending tasks remain, prints one statement to stdout and returns 0 without creating `TerminalSupport`, `DisplayManager` or any display. `DisplayDecision` keeps its current pure signature.

Both halves of the condition matter: `getRunnableTasks()` empty while `hasAnyPendingTasks()` is true is the existing dependency-deadlock case, which keeps its current error log and must not be reported as "nothing to run".

The statement goes to stdout rather than through SLF4J because the root logger is set to `WARN` whenever the console execution trace is not active, so an INFO message would be invisible in exactly the two modes that need it.

Alternatives considered:

- *A new `DisplayDecision.Mode.IDLE`*: puts a scheduling fact into a pure display-decision function and would still need the statement printed by the caller. Rejected; the decision stays about the console, the idle check stays about the DAG.
- *Keep the TUI and add a line to the frame*: keeps a 0-second TUI flash and an `Analysis Complete` summary that reports no work, which is what the user reported as wrong. Rejected.

### D3: Declare native access in the artefact, document it for the other run paths

The executable jar declares `Enable-Native-Access: ALL-UNNAMED` in its manifest (`maven-jar-plugin` `manifestEntries`), which JDK 24+ honours for `java -jar`. JVM arguments passed on the command line cannot be set from inside the process, so the other documented run path (`mvn exec:java`) needs the equivalent `--enable-native-access=ALL-UNNAMED` documented; `.mvn/jvm.config` is not touched, because that would apply the flag to every Maven invocation of the project, including builds that never start a terminal.

The flag is additive and harmless without JLine: it only lifts restrictions from unnamed-module code.

Alternatives considered:

- *Always require users to pass the flag*: leaves a warning and, on a future JDK, a silently degraded TUI. Rejected.
- *Switch to a JNI-based JLine provider or drop JLine*: removes the warning by adding another native dependency or losing the terminal abstraction. Rejected.
- *Set `shell_security`/JVM options in the launcher scripts*: the project ships no launcher script; `java -jar` and `mvn exec:java` are the paths.

### D4: Cover the three behaviours at the level where they can fail

- First-frame statuses: unit test on `DisplayManager`/`ProgressDisplay` with the existing test terminal, asserting the first painted frame contains the successful marker for a pre-completed task and no pending marker for it. This is the regression guard for the ordering, and it also covers partial reruns.
- Idle run: artefact test that builds a workspace whose `state.json` marks every task of the analysed task list as successful (the ids are read from `tasks.yaml` at test time, so the fixture does not rot when the task list changes), runs the packaged jar against it, and asserts the statement appears, no TUI frame is painted and the run exits 0.
- Native access: the artefact test inspects the jar manifest for the attribute and asserts that a `java -jar` run emits no restricted-native-access warning. The `-cp` probe runs inside the same test get the flag passed explicitly, because the manifest attribute only applies to `java -jar`.

## Risks / Trade-offs

- [The idle early return also suppresses the completion summary in `--execution-trace` mode, where users currently see config and task logs] → the statement names the task count and the run keeps all its other console output in trace mode; results stay in `analysis.json`, and `state dump` shows per-task state.
- [A run whose tasks are all successful but that the user expected to re-run would now say nothing was done] → that is the current behaviour too (nothing executes either way); re-running is done with `state clear` / `state drop`, which the statement is expected to mention.
- [`Enable-Native-Access` only helps `java -jar`] → documented for `mvn exec:java`; the attribute is ignored by older JDKs, which is the same behaviour as today (warning only).
- [Changing `addTasks` to a two-argument method touches existing tests] → overload keeps the old signature; the existing tests are updated only where they assert the old ordering.
- [Suppressing the TUI for an idle run could hide a real scheduling problem] → the condition requires "no runnable tasks **and** no pending tasks"; the deadlock case still logs an error and is not reported as idle.
- [A terminal that is not a terminal in the idle case is never probed, so the display-mode notice is not printed] → the statement is printed unconditionally in the idle path, so the run is never silent.

## Migration Plan

1. Change `ProgressDisplay`/`DisplayManager` ordering (D1) and the `AnalyseCmd` idle path (D2).
2. Add the manifest entry (D3) and the documentation line for `mvn exec:java`.
3. Add the unit and artefact tests (D4); run `mvn clean verify`.
4. Rollback: revert the commit; no workspace data, configuration or analysis result format changes, so nothing needs migrating.

## Open Questions

- Whether the idle statement should be translated or stay English like the rest of the console output.
- Whether a future change should make the idle run print a short summary of the existing results (for example counts already in `analysis.json`).
