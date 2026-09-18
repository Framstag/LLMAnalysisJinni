## 1. Configuration precedence

- [x] 1.1 Confirm the Picocli API used for explicit-set detection (`ParseResult.hasMatchedOption` or equivalent) against the pinned Picocli version, and verify with a scratch `--help`/parse check that an option which was not passed is not reported as matched while an option passed with its default value is. If the API is unsuitable, switch to nullable wrapper fields instead and note the change in design.md Decision 1.
- [x] 1.2 Remove the initialisers and `defaultValue` attributes that make CLI options indistinguishable from unset on `AnalyseCmd` (`logRequest`, `logResponse`, `executionTrace`, `executionTraceSystem`, and any other overridable option), and verify the module compiles with `mvn -q compile`.
- [x] 1.3 Add a pure merge helper that applies only the explicitly passed overrides onto a loaded `Config`, in the `cli` or `config` package, and verify the precedence tests below pass.
- [x] 1.4 Add unit tests for the merge covering: explicit flag beats config, config beats built-in default, absent flag leaves the config value untouched, explicit value equal to the default still overrides config; verify with `mvn -q test -Dtest=<MergeTestClass>`.
- [x] 1.5 Add unit tests covering the overridable option inventory from the `cli-config-precedence` spec (request logging, response logging, execution trace, system message trace, task parallelism); verify each setting resolves independently and that no setting is written when its option is absent.
- [x] 1.6 Make the effective value and its source (command line, `config.json`, or built-in default) visible in the run log; verify by running an `analyse` with one overridden and one inherited setting and reading the log output.

## 2. Display mode resolution

- [x] 2.1 Resolve the execution trace once after the merge and derive the Logback root level, the display choice, and the `ChatExecutor` trace from `config.isExecutionTrace()`; verify by code inspection that `AnalyseCmd` no longer reads its own `executionTrace` field after the merge.
- [x] 2.2 Replace the `System.console()` TUI gate with capability detection derived from the JLine terminal, constructing the terminal once and passing it into the display layer; verify by running `analyse` under `mvn exec:java` (fallback expected) and in a real terminal (TUI expected).
- [x] 2.3 Report the active display mode and the reason the TUI was not started, printing nothing extra when the TUI does start; verify with a non-TTY run (`analyse <workspace> | cat`) that exactly one such line appears and no cursor escape sequence is present in the output.
- [x] 2.4 Add unit tests for the display-mode decision function with injected terminal-capability values, covering: capable stdout and trace off yields TUI, incapable stdout yields the simple fallback, and trace active yields neither TUI nor fallback; verify with `mvn -q test -Dtest=<DecisionTestClass>`.

## 3. TUI rendering correctness

- [x] 3.1 Route task failure through the failure path so a failed task row is marked failed rather than completed; verify with a unit test asserting the failure status after an error event, and by observing a deliberately failing task render as a failure.
- [x] 3.2 Gate cursor movement and screen erase on terminal capability, and emit no escape sequence when capability is absent; verify by capturing TUI output with a dumb terminal and asserting it contains no escape sequences.
- [x] 3.3 Track the height of the previously rendered frame and move the cursor up by exactly that amount, with no cursor movement on the first paint; verify by running the TUI in a terminal that already contains output and confirming the earlier output is not scrolled over or corrupted.
- [x] 3.4 Avoid redundant repaints when the frame is unchanged, so the render timer cannot flood a slow terminal; verify by observing steady output volume while a long task is idle.

## 4. Integration and verification

- [x] 4.1 Run `mvn verify` and confirm the existing test suite plus the new tests pass.
- [x] 4.2 Update the user-facing documentation for the CLI options whose defaults changed (`--execution-trace`, `--log-request`, `--log-response`, `--task-parallelism`) and the precedence rule; verify the documented precedence matches the behaviour observed in 1.6 and 2.3.
- [x] 4.3 Run `openspec validate fix-cli-config-precedence-and-tui-fallback --strict` and confirm the change still validates against the final implementation.
