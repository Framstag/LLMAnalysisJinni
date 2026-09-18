# Tasks

## 1. Correct the first TUI frame

- [x] 1.1 Add `ProgressDisplay.addTasks(List<TaskDefinition>, Set<String> preCompletedTaskIds)` which applies the successful status while building the rows and renders exactly once at the end, keeping `addTasks(List)` as an overload delegating with an empty set; verify the existing display tests still pass with `mvn test -Dtest='ProgressDisplayTest,DisplayManagerTest,TaskRowTest,LoopWorkerRowTest'`
- [x] 1.2 Pass the pre-completed task ids into the new method from `DisplayManager` and drop the marking loop that runs after the first paint; verify a unit test in `DisplayManagerTest` asserts the first painted frame contains the successful marker for a pre-completed task and contains no pending marker for it

## 2. Report a run with nothing to execute

- [x] 2.1 In `AnalyseCmd.call()`, compute the runnable tasks before any display is created and, when there are none and `hasAnyPendingTasks()` is false, print a statement that no task is runnable and that all tasks are already successful, then return 0 without probing a terminal or creating a `DisplayManager`; verify by running the packaged jar against `workspaces/spring-petclinic` (36 tasks already successful) and observing the statement, no TUI frame and exit status 0
- [x] 2.2 Keep the dependency-deadlock case out of the idle path: no runnable tasks while pending tasks remain SHALL still log the existing deadlock error and SHALL NOT print the nothing-to-run statement; verify with a unit-level check of the condition and with the negative artefact test from 4.3

## 3. Remove the restricted native access warnings

- [x] 3.1 Declare `Enable-Native-Access: ALL-UNNAMED` in the manifest of the executable jar in `pom.xml`; verify `unzip -p target/LLMAnalysisJinni-jar-with-dependencies.jar META-INF/MANIFEST.MF` shows the entry and that a `java -jar` run emits no `WARNING: A restricted method` line
- [x] 3.2 Document the equivalent `--enable-native-access=ALL-UNNAMED` argument for the `mvn exec:java` run path in `README.md` and `AGENTS.md`, including why it exists; verify both files mention the flag and that the documented commands match the verified behaviour

## 4. Regression coverage in the artefact test

- [x] 4.1 Extend `JarSmokeIT` with a test that generates a workspace whose `state.json` marks every task of the analysed task list as successful (task ids read from `tasks.yaml` at test time), runs the packaged jar against it, and asserts the run states that no task is runnable, paints no TUI frame and exits 0
- [x] 4.2 Extend `JarSmokeIT` to assert the jar manifest declares native access for the unnamed module and that a `java -jar` run contains no restricted-native-access warning; pass `--enable-native-access=ALL-UNNAMED` explicitly to the `-cp` probe runs, which the manifest entry does not cover
- [x] 4.3 Add the negative case to `JarSmokeIT`: a workspace with a pending task whose model endpoint is a closed local port SHALL NOT print the nothing-to-run statement, so the idle path stays distinguishable from a run that has work
- [x] 4.4 Run `mvn clean verify` on the final state, confirm the unit tests and all artefact tests pass, and record the exact commands executed against the packaged jar as evidence

## 5. Follow-up observed while implementing

- [x] 5.1 Record that an idle run still prints the configuration and tool-initialisation INFO lines before the statement, because the root logger is only lowered to `WARN` when the display is initialised, and that this run no longer initialises a display; those lines are the run log the `cli-config-precedence` requirement asks for, so the behaviour is kept deliberately and closing the change includes no follow-up task for it
