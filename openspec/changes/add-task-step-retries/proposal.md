# Proposal

## Why

A task step that returns nothing, returns no JSON payload, returns an unparseable payload, or returns a payload that violates its declared response schema ends that task for the whole run. The engine only recovers on the next full `analyse` invocation, and the TUI and the logs report a bare failure without ever asking the model again. Most of these outcomes are self-correctable: a model that answered in prose, or dropped a required field, usually answers correctly when told what was wrong. A schema violation is worse than a failure today, because it is silently accepted and counted as a success.

## What Changes

- **In-run retry per task step.** A step is attempted again when the model returned no response text, returned no JSON payload, returned a payload that does not parse, returned a payload that violates the declared response schema, or raised a retriable model error (timeout, rate limit, internal server error). A step is one non-loop task execution, or one loop index worker; each loop index succeeds or fails on its own, as it does today.
- **New `retries` configuration option**, `config.json` only, default 3, meaning the maximum number of attempts for one step (3 = one initial attempt plus two retries). Values below 1 are rejected as configuration errors. No CLI option.
- **Fresh conversation per attempt** with a repair hint appended to the user message from attempt 2 on. The hint states the reason class and, for a schema violation, the violation messages, plus the parser message and excerpt for a payload failure.
- **Non-retryable outcomes fail immediately** without spending an attempt: non-retriable model errors, unsupported-feature and tool exceptions, and IO failures such as a chat log that cannot be written.
- **BREAKING — a schema violation is no longer a success.** A step whose attempt budget is exhausted is marked failed and writes no response property, including a parsable but non-conformant payload. Its tags do not unlock dependents, and the next run executes the task again.
- **BREAKING — per-attempt chat logs.** Attempt 1 keeps the current filename (`<taskId>[_<loopIndex>].log`); attempts from 2 on are written as `<taskId>[_<loopIndex>].attemptN.log`, so the transcript of a rejected attempt survives.
- **Retry hints in every output**: an attempt counter on the TUI task and worker rows, a retry line in the piped/CI output, and one WARN engine log record per retry carrying the bounded reason.
- **`ChatExecutor` reports a typed step outcome** instead of logging a schema violation as a warning and returning the payload.

## Capabilities

### New Capabilities

- `llm-response-retry`: the per-step attempt budget and its configuration, the failure classification that triggers another attempt, the repair hint, the rule that only a conformant attempt is stored, and the retry hints in the TUI, the piped/CI output and the engine log.

### Modified Capabilities

- `task-failure-handling`: "A schema violation is not a failure" is replaced, because an exhausted attempt budget with a non-conformant response is now a failure; the existing cross-run retry requirement is reconciled with the new in-run attempts.
- `llm-response-schema-validation`: "Diagnostic-only validation" and "Non-interference" are replaced, because the validation outcome now selects the step outcome instead of being inert.
- `llm-interaction-logger`: the requirement that the file path is deterministic and existing log files are overwritten with no versioning is replaced by the per-attempt naming rule.
- `live-progress-display`: the "log files always written regardless of display mode" requirement's path pattern gains the retry suffix.

## Impact

Affected code:

- `config/Config.java` — `retries` field, default, dump
- `config/ConfigLoader.java` / `config/ConfigStorer.java` — validation of values below 1 and the serialized default
- `lc4j/ChatExecutor.java` — returns a step outcome, composes the repair hint, reports instead of warning
- `lc4j/ChatLogger.java` — attempt-aware filename
- `cli/AnalyseCmd.java` — both call sites (loop index worker and non-loop task) go through the retry policy
- new retry policy type and step outcome type
- `display/ProgressCallback.java`, `ProgressDisplay.java`, `TaskRow.java`, `LoopWorkerRow.java`, `SimpleOutput.java`, `DisplayManager.java` — retry reporting
- tests: `lc4j/ChatExecutionLoggingTest.java`, `config/ConfigOverridesTest.java`, plus new tests for the retry policy, the outcome type, hint composition and the log filename

Affected docs: `README.md` configuration table (new `retries` row) and `AGENTS.md` where the workspace configuration and the state/outcome rules are described.

Not affected: `analysis/software-architecture/tasks.yaml`, prompts, response schemas, MCP tools, DAG scheduling and `state.json` format.

## Open Questions

Recorded rather than decided, so the design and tasks phases can settle them:

- Whether the plain log filename should mean "attempt 1" (positional, no reordering needed) or "the attempt that decided the step" (requires writing the log after validation, which currently runs after the log write).
- The bound on the hint text: the violation list and the parser excerpt are unbounded today, and both are injected into a prompt.
- Whether `state.json` should record the attempt count and the last failure reason for `state dump`.
- Whether a `--retries` CLI option is wanted at all. Adding it also changes the `cli-config-precedence` capability and needs a delta for it.
- Whether attempts should be separated by a delay. The current assumption is no delay: retriable model errors are rare, and the engine has no other sleep policy.
