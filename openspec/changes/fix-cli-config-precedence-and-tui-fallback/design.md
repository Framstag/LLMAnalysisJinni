## Context

See proposal.md - Why for the motivation. The constraints that shape this design:

- `analyse` loads `config.json` first and then assigns CLI option fields into it. All those fields live on `AnalyseCmd` (`src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java`), so the merge is entangled with Picocli parsing and is currently untestable in isolation.
- Two different places decide display and logging today: `AnalyseCmd` uses the CLI field `executionTrace` for the TUI decision and for the Logback root level, while the rest of the engine reads `config.isExecutionTrace()`. The two agree only by accident of the unconditional overwrite.
- JLine 3 is already a dependency and `ProgressDisplay` already constructs a terminal. Terminal capability information is therefore available without new dependencies.
- `ProgressDisplay` is the only writer of cursor escapes, and it currently emits them unconditionally while gating only colour on `ansiSupported`.

## Goals / Non-Goals

**Goals:**

- One resolved configuration object per run, decided before the engine starts, so every consumer (display decision, Logback level, `ChatExecutor` trace, task pools) reads the same values.
- The override merge is a pure, unit-testable function with no Picocli types in it.
- Display-mode decision and terminal capability are derived from the same terminal object that renders the TUI.
- Terminal control output degrades safely: no escape sequences without capability, and no cursor movement above lines the TUI did not write.

**Non-Goals:**

- No change to `config.json` keys, format, or serialization.
- No new Maven dependency; no replacement of JLine.
- No rework of `ChatExecutor`'s tracing conditions beyond reading the resolved value.
- No change to how `workspace init` writes defaults, beyond the fact that the execution trace default is now disabled everywhere.

## Decisions

### Decision 1: Detect "option was passed" with the Picocli parse result

`AnalyseCmd` receives the parsed command line, and `ParseResult.hasMatchedOption(...)` answers whether the user actually supplied an option. Fields stay as they are; the merge asks the parse result instead of relying on field initialisers.

```
Picocli parse ──> ParseResult.hasMatchedOption("--log-response")?
                        |
              yes ──────+────── no
               |                 |
   apply CLI value        keep loaded config value
```

Alternatives considered:

- *Nullable wrapper fields without initialisers or `defaultValue`.* Works and is the smallest diff, but keeps the tri-state implicit in field nullability, which is what caused the issue, and leaves the primitives (`boolean executionTrace`) needing type changes anyway.
- *`set` callback per option recording into an override map.* Equivalent outcome, more code, easy to forget for a new option.

Chosen because it makes "was it passed" explicit in the option model rather than in field defaults, and it keeps existing field types.

### Decision 2: Put the merge in a pure helper, not in `AnalyseCmd.call()`

A small value object holds the overrides that were actually present, and a pure function applies them to a loaded `Config`. `AnalyseCmd` only builds the override object from the parse result and calls the function.

Rationale: the repository tests behaviour with plain JUnit and no CLI harness (`guidelines/TestApproach.md`), so the precedence rules from `cli-config-precedence` become directly testable, including "omitting an option leaves the configuration untouched".

### Decision 3: Resolve the execution trace once, and derive everything from `config.isExecutionTrace()`

After the merge, `AnalyseCmd` no longer consults its own `executionTrace` field. The TUI decision, the Logback root level, `DisplayManager` construction, and `ChatExecutor` all read `config.isExecutionTrace()`.

Alternative considered: keep passing the CLI field alongside the config. Rejected because it recreates the two-sources-of-truth bug being fixed.

```
effective trace = CLI flag if passed, else config.json, else false (default)

  trace active  ──> verbose SLF4J console, no TUI
  trace inactive ──> Logback WARN, TUI if stdout capable, else SimpleOutput
```

### Decision 4: Decide TUI capability from the JLine terminal, not from `System.console()`

`DisplayManager` (or its caller) constructs the JLine terminal once and passes it to `ProgressDisplay`, instead of `ProgressDisplay` building its own. Capability is derived from the terminal itself: a dumb terminal type, or the absence of the cursor-movement capability, means no cursor control.

Rationale: `System.console()` answers "does this JVM have a console object", not "is stdout a terminal with cursor support", which is the actual question. JLine already models exactly that.

Alternatives considered:

- *Env-var heuristics (`TERM`, `CI`).* Brittle, and wrong for the IDE/`mvn exec:java` case that motivates the change.
- *Keep `System.console()` as an additional gate.* Rejected: it is precisely the probe that makes the TUI silently disappear.

### Decision 5: Report the fallback mode and reason explicitly

When the TUI is not started, the run emits one line stating the active display mode and why the TUI was not used (console trace active, or stdout not a terminal). Nothing extra is printed when the TUI does start.

Rationale: the reported symptom was "no error and no TUI"; a silent downgrade must be impossible.

### Decision 6: Track the rendered frame height instead of guessing it

`ProgressDisplay` records how many lines its previous frame occupied and moves the cursor up by exactly that amount, and it never moves the cursor on its first paint. All cursor and erase writes are gated on terminal capability.

Alternatives considered:

- *Recompute the frame row count and rely on it matching the previous frame.* Rejected: task and worker rows appear between paints, so the computed count drifts from what is on screen.

### Decision 7: Keep the existing `--execution-trace=true|false` syntax

The option keeps `arity = "1"`, so `--execution-trace=true` and `--execution-trace=false` continue to work for existing invocations. Absence is now meaningful, which already fixes the reported problem.

Alternative considered: a negatable switch (`--execution-trace` / `--no-execution-trace`), which reads better but changes accepted syntax. Deferred to the open questions rather than bundled into a bug fix.

## Risks / Trade-offs

- [Behaviour change is intentional and user-visible: a workspace whose `config.json` has the execution trace enabled will now get verbose console output and no TUI, while one without it gets the TUI where previously the CLI default decided] → Resolved by Decision 5's report plus the existing `config.dumpToLog()` output stating the effective value and its source; README/Models-adjacent docs updated in the task list.
- [Newly unset defaults change `logRequests`/`logResponses` for existing workspaces that stored `false` while the user passed `--log-response true`] → Expected: the config value now wins unless the flag is passed. The effective-value report makes it visible.
- [`ParseResult.hasMatchedOption` is a Picocli API whose exact name and behaviour (matched vs. defaulted) must be confirmed against the pinned Picocli version] → Verify during the first implementation task; if unsuitable, fall back to nullable wrapper fields, which is an internal change and does not affect the specs.
- [Terminal capability detection can disagree with reality on unusual terminals, producing a TUI without cursor support or an unnecessary fallback] → Gate all escape output on the capability result so a wrong answer degrades to plain text rather than garbage, and cover the decision function with unit tests using injected capability values.
- [The failure-routing fix (`onTaskError` currently marks the task successful) may reveal analysis runs that previously hid task errors] → Acceptable: the spec already requires failure to be shown as failure; the task list records that this may surface previously invisible failures.
- [Printing the fallback line adds output to piped/CI runs] → One line per run, on stdout, before task output; it does not interfere with parsing task lines because it has no status icon prefix.

## Migration Plan

- No data migration: `config.json` keys and format are unchanged, and no workspace needs rewriting.
- Deploy: single commit containing the merge fix, the display decision change, and the rendering guards; the three spec deltas archive with it.
- Rollback: revert the commit. Existing workspaces keep working because the accepted CLI syntax is unchanged.
- Verification: `mvn verify`, plus a manual `analyse` run under `mvn exec:java` (no TUI expected, fallback reported) and in a real terminal (TUI expected).

## Open Questions

- Whether to switch `--execution-trace` to a negatable switch (`--no-execution-trace`) as a follow-up CLI-UX change. Deferrable: it changes accepted syntax, not the precedence model or the specs.
- Whether the fallback report line belongs on stdout or stderr. Deferrable: the requirement only demands that the mode and reason are reported; stdout is the working default because all other display output goes there.
- Whether `workspace init` should stop writing an explicit execution trace value now that the default is disabled. Deferrable: `config.json` format does not change, and an absent key resolves to the same default.
