# Proposal

## Why

While the TUI paints the task list, other parts of the process keep writing to the same stream.
`logback.xml` attaches a `ConsoleAppender` to `System.out`, and `AnalyseCmd` only lowers the root
level to `WARN` when the execution trace is off, so every `WARN` and `ERROR` — JSON corrections,
schema violations, one per violation, task failures from parallel workers — lands in the middle of
a frame that JLine is drawing on `System.out`. A real run ends with a task table, an aggregate
token footer and then a logback record with a timestamp on top of it.

This contradicts the existing requirement: `live-progress-display` already states that when the
execution trace is not effective and stdout is a terminal, "SLF4J console output SHALL be
suppressed". Suppression today means "quieter than before", not suppressed. Three direct
`System.err.println` calls in the analysis tools (`CsvReportWriter`, `JavaTool.createTypeResolver`)
bypass logging entirely and can do the same. There is also no log file appender in the engine, so
raising the threshold any further would delete the diagnostics instead of relocating them.

## What Changes

- While the TUI owns the terminal, engine log output SHALL NOT be written to the terminal.
- Engine logs SHALL be written to a per-run file under the workspace (`logs/engine.log`,
  overwritten per run) so the diagnostics survive.
- The TUI SHALL show the latest `WARN` or `ERROR` as a single truncated line inside its frame, so
  a failure that is not tied to one task row is visible while the run is still going on.
- Direct writes to `stdout`/`stderr` in analysis tool code SHALL be routed through the engine's
  diagnostic path, so they cannot bypass the routing.
- Non-TUI display modes keep today's console behaviour: the simple/piped mode continues to print a
  `WARN`/`ERROR` diagnostic line to the console (the packaged-artefact smoke test depends on it),
  and `--execution-trace` keeps the verbose console output unchanged.
- The requirement that "SLF4J console output SHALL be suppressed" while the TUI is active becomes
  true as written; the corresponding statement about "only task-level logs and errors" on the
  console is qualified to the non-TUI case.

## Capabilities

### New Capabilities

None. The behaviour belongs to the existing display and interaction-logging capabilities.

### Modified Capabilities

- `live-progress-display`: the requirement that SLF4J console output is suppressed while the TUI is
  the display mode gains the concrete meaning of "not written to the terminal, written to a log
  file instead", and the capability gains the in-frame recent-warning line.
- `llm-interaction-logger`: the scenario that promises task-level logs and errors on the console
  when the console trace is off is qualified to runs in which the TUI does not own the terminal.

## Impact

- `src/main/resources/logback.xml` and the logback reconfiguration in
  `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java` (the console appender is detached and a
  file appender attached once the display decision is known).
- `src/main/java/com/framstag/llmaj/display/ProgressDisplay.java` and `DisplayManager.java` — the
  in-frame log line and the additional frame row.
- `src/main/java/com/framstag/llmaj/tools/common/CsvReportWriter.java` and
  `src/main/java/com/framstag/llmaj/tools/java/JavaTool.java` — the three direct `System.err`
  writes.
- Tests: `display/ProgressDisplayTest` (frame content and row accounting), a test for the appender
  routing, and the packaged-artefact smoke test must stay green because the console diagnostic in
  the piped case must survive.
- No new dependency. No change to task scheduling, the DAG, retry behaviour, or the analysis
  results. `workspaces/spring-petclinic/logs/<taskId>.log` and the `<workspace>/logs/engine.log`
  file are different artefacts for different purposes.
- Independent of `fix-json-payload-extraction`: that change removes one (over-large) log body and
  changes which responses parse; this one changes where log output goes.
