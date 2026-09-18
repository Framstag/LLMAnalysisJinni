# Proposal

## Why

Running `analyse` against a workspace whose tasks are all already successful produces a display that contradicts itself and that never has anything to show. The TUI is entered, paints exactly one frame in which every task is listed as pending (`…`), and is then closed a few milliseconds later by a completion summary that lists the same tasks as successful (`✓`). Nothing anywhere states that there was nothing to execute, so a 0-second run with 0 tokens reads like a silent failure rather than "this workspace is already analysed".

Two defects cause this, and a third makes the TUI brittle on the JVM the project targets:

- `DisplayManager` marks tasks that were already successful *after* `ProgressDisplay.addTasks()`, and `addTasks()` is what paints the first frame. The statuses are therefore applied to a frame nobody sees; the correcting repaint needs the 500 ms render timer, which never fires in a run that ends immediately. The same ordering also misreports every partially-analysed workspace on rerun, until the first repaint.
- A run with nothing to execute still enters the TUI, and the completion summary is the only thing that says anything useful. Neither the TUI path nor the simple-output path says "nothing to run".
- JLine's terminal on Java 25 uses restricted `java.lang.foreign` methods and prints four `WARNING: A restricted method …` lines into the middle of the TUI output. The JDK will block these methods in a future release, at which point the terminal degrades to a dumb terminal and the TUI silently disappears.

## What Changes

- Pre-completed tasks SHALL be marked before the TUI paints its first frame, so the first frame already shows their real status.
- A run that has nothing to execute SHALL say so explicitly and SHALL NOT start the live TUI for it: no 0-second TUI flash, no misleading pending table, no "Analysis Complete" summary pretending that work happened.
- The packaged artefact SHALL declare the native access its terminal implementation requires (`Enable-Native-Access: ALL-UNNAMED` in the jar manifest), and the documented run paths SHALL pass the equivalent flag, so the warnings disappear and the TUI keeps working when the JDK starts blocking restricted methods.
- Add regression coverage: a unit test for the first-frame statuses, an artefact check that a fully-analysed workspace reports "nothing to run" without starting the TUI, and an artefact check that no restricted-native-access warning is emitted.

## Capabilities

### New Capabilities

None. All three changes are behaviour of the existing display subsystem.

### Modified Capabilities

- `live-progress-display`: the first frame must reflect already-successful tasks instead of painting them as pending, a run with nothing to execute must be reported and must not open the TUI, and the TUI must run without restricted native access warnings on the supported JVM.

## Impact

- `src/main/java/com/framstag/llmaj/display/DisplayManager.java` — ordering of `addTasks` and the pre-completed marking.
- `src/main/java/com/framstag/llmaj/display/ProgressDisplay.java` — a way to paint the initial frame once the statuses are final.
- `src/main/java/com/framstag/llmaj/display/DisplayDecision.java` and `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java` — the nothing-to-execute path and its statement.
- `pom.xml` — `Enable-Native-Access` manifest entry for the executable jar.
- `README.md` / `AGENTS.md` — native access flag for the documented run paths.
- Tests — `display` unit tests plus the artefact smoke test.
- No change to task scheduling, the DAG, the analysis results, the retry behaviour, or the `--execution-trace` output.
- Independent of `fix-shaded-jar-class-stripping`: that change made the jar's own output work at all; this one fixes what the output says when there is no work.
