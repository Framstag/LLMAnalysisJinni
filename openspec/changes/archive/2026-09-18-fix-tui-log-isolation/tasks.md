# Tasks

## 1. Route engine logs away from the terminal

- [x] 1.1 Replace the root-level change in `AnalyseCmd` with the two-branch reconfiguration: on the TUI branch detach the console appender, attach the engine log file appender and the in-frame appender; on the non-TUI branch keep today's behaviour. Verify with a unit test that the TUI branch leaves no console appender attached and the non-TUI branch leaves the console appender attached.
- [x] 1.2 Add the engine log file appender: `<workspace>/logs/engine.log`, overwritten per run, directory created when missing, level `INFO`, pattern carrying level, logger and MDC `taskId`. Verify with a unit test that a record emitted in the TUI branch lands in the file and that a second run's file contains only the second run's records.
- [x] 1.3 Confirm the run survives a log file that cannot be opened (read-only workspace): the run continues, no task is marked failed for it. Verify by pointing the workspace log directory at a path that cannot be written and asserting the analysis still completes.
- [x] 1.4 Keep a diagnostic produced by the routing itself (engine log file cannot be created or opened) from being lost: install the appenders before the display exists but detach the console only after the first frame has been painted. Added after the manual check showed the console already detached while the in-frame sink still had no target, so the warning went nowhere. Verify with a test that the routing report is emitted and with the existing unusable-log-file test.

## 2. Show the latest warning or error in the frame

- [x] 2.1 Add the sink interface and the forwarding logback appender attached at `WARN` level in the TUI branch, passing the display as the sink through `DisplayManager`. Verify with a unit test that a `WARN` record reaches the sink with its level, logger and task id.
- [x] 2.2 Reserve one frame row for the latest `WARN`/`ERROR`, truncated to the available width, empty when nothing was reported, and render it in both the ANSI and the dumb-terminal frame paths. Verify with `ProgressDisplayTest` cases for empty, one record, over-wide record, and a newer record replacing the older one.
- [x] 2.3 Re-check the frame accounting after the extra row: a repaint overwrites exactly the region it painted, and the first paint does not move the cursor above pre-existing output. Verify by updating the existing `ProgressDisplayTest` assertions deliberately and re-running them.
- [x] 2.4 Paint the reserved line when the record arrives (debounced) and once more when the display closes, so a record that arrives after the last rendered frame is still shown. Added after the manual check of task 4.3 showed a record reaching only the log file 0.3 s before the run ended. Verify with `ProgressDisplayTest` cases for a record arriving just before `close()` and for a burst of ten records painting fewer than ten frames.

## 3. Stop tool code from writing to the terminal

- [x] 3.1 Add loggers to `CsvReportWriter` and `JavaTool.createTypeResolver` and emit the existing messages at `ERROR` instead of `System.err.println`. Verify with a unit test that a failing CSV write is reported through the logger and that no bytes are written to the captured error stream.
- [x] 3.2 Search the main sources for remaining direct console writes and justify each one that stays (the display-mode report and the nothing-to-run statement happen before the TUI starts). Verify with a search over `src/main/java` showing only those two, plus `SimpleOutput` which is not used in TUI mode.

## 4. Verify

- [x] 4.1 Run `mvn verify` and confirm unit tests and the artefact smoke test pass in one run, in particular that the piped configuration-failure diagnostic still reaches the console.
- [x] 4.2 Run `openspec validate fix-tui-log-isolation --strict` and confirm the change validates.
- [x] 4.3 Manual check on a real TTY: run `analyse` on `workspaces/spring-petclinic` and confirm no timestamped log record appears in or between frames, that a `WARN` record shows on the reserved line, and that `<workspace>/logs/engine.log` contains the records of that run. Verified with `script` on 2026-09-18: `analyse -o BuildSystems workspaces/spring-petclinic` showed `! [BuildSystems] Corrected JSON String to: …` on the reserved line in two frames, the terminal carried only the 31 pre-TUI startup records, and `<workspace>/logs/engine.log` held the records of that run with the mtime of that run.

## 5. Verification follow-ups

Raised by the verification pass after the tasks above were complete. No behaviour change except the task-identifier assertion.

- [x] 5.1 Assert that the engine log file identifies the task the record was emitted for: `EngineLogRoutingTest.testTuiBranchWritesRecordsToTheEngineLogFile` now sets the MDC task key and asserts `[SomeTask]` in the file, which is the scenario "Records carry the task identifier" that only the manual run had proven.
- [x] 5.2 Use `EngineLogRouting.TASK_ID_MDC_KEY` at both MDC call sites in `AnalyseCmd` instead of the duplicated `"taskId"` literal, so the pattern, the appender lookup and the writer cannot drift apart.
- [x] 5.3 Cover the second tool diagnostic: `createTypeResolver` is package private (as `AnalyseCmd.readOverrides` is, for the same reason) and `JavaToolTest` now feeds it a JAR file that is not an archive and asserts the report goes through the logger while the error stream stays empty.
- [x] 5.4 Correct the design's test description: the routing test asserts that the console appender is no longer attached rather than capturing the console stream, and the tool diagnostics test covers an unreadable JAR directory as well.
- [x] 5.5 Document the new log file: `AGENTS.md` workspace layout and `README.md` analysis output both name `logs/engine.log` and the in-frame warning line.
