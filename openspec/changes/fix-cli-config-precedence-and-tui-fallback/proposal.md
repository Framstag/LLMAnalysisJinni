## Why

`analyse` merges its CLI options into the loaded workspace config by unconditionally assigning every option field, so the built-in defaults in `Config` and the field initialisers in `AnalyseCmd` always win over `config.json`. Reported as issue #11 for `--log-response`: the `Boolean` field is initialised to `false`, the `!= null` guard around the assignment is therefore dead code, and a user-set `"logResponses": true` in `config.json` is silently discarded. The same defect applies to `--log-request` and, worse, to `--execution-trace` and `--execution-trace-system`, which have no guard at all.

A second, related defect makes the display mode unpredictable: `analyse` decides whether to start the TUI from `System.console() != null`. That probe is `null` under `mvn exec:java`, IDE run configurations, and redirected stdin, so the TUI silently never starts, `SimpleOutput` takes over, and the user sees neither an error nor the expected TUI.

## What Changes

- **CLI options no longer overwrite config by default**: an option only writes into the config when the user actually passed it. Precedence becomes explicit CLI value > `config.json` value > `Config` built-in default. Covers `--log-request`, `--log-response`, `--execution-trace`, `--execution-trace-system`, `--task-parallelism`.
- **Display mode selected by stdout TTY detection**: the TUI starts when stdout is a terminal, independent of the JVM console probe.
- **Fallback is reported**: when the TUI cannot start, the chosen fallback mode and the reason are logged, so a silent downgrade cannot happen again.
- **TUI renders failures as failures**: a failed task row is marked failed instead of being routed through the success path. This behaviour is already required by `live-progress-display`; only the implementation is wrong, so no requirement changes for it.
- **Cursor and erase escapes become conditional**: no cursor movement is emitted on a terminal that does not support ANSI, and the first paint no longer scrolls over prior shell output.
- **`--execution-trace` option UX settled**: it stays the opt-in verbose console mode that disables the TUI, with a consistent boolean syntax documented in the CLI help.

Non-goals: no change to `config.json` format or key names, no new dependency, no change to which log files are written.

## Capabilities

### New Capabilities
- `cli-config-precedence`: rules for combining explicitly passed CLI options, workspace `config.json` values, and built-in defaults, including the requirement that each overridable option distinguishes "not supplied" from "supplied with the default value".

### Modified Capabilities
- `live-progress-display`: display-mode selection changes from JVM console probing to stdout TTY detection; non-TTY fallback must report why it was chosen; cursor control must be conditional on ANSI support and must not disturb pre-TUI output.
- `llm-interaction-logger`: the console trace no longer defaults to `true`; it follows the configuration precedence order, and an active console trace disables the TUI.

## Impact

- **Modified files**: `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java` (option fields, config merge, display-mode decision), `src/main/java/com/framstag/llmaj/display/DisplayManager.java` (error routing), `src/main/java/com/framstag/llmaj/display/ProgressDisplay.java` (ANSI-conditional rendering, paint baseline).
- **Potential modified file**: `src/main/java/com/framstag/llmaj/config/Config.java` only if the merge needs a marker for absent values; no key changes.
- **Tests**: new unit tests for the CLI/config merge precedence and for display-event routing (failure vs success).
- **CLI behaviour**: `--log-request`, `--log-response`, `--execution-trace`, `--execution-trace-system` and `--task-parallelism` may now be left unset so that `config.json` takes effect. This is the intended fix, but it changes observed behaviour for workspaces whose `config.json` disagrees with the previous always-overwrite defaults.
- **No API or dependency changes**; no config format migration required.
