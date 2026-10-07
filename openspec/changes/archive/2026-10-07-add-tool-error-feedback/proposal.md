# Proposal

## Why

Issue #14, plus the asymmetry it exposes. Three defects in the engine's tool-call loop:

1. **A tool argument the framework cannot coerce ends the step.** When a model emits a bare JSON string for a parameter that is declared as an array (`"pom.xml"` instead of `["pom.xml"]`), lc4j raises a `MismatchedInputException`, the engine's arguments error handler rethrows it, and the step is lost without any further attempt. Two tools carry array-typed parameters today: `filesystem_get_matching_files_in_dir_recursively` (whitelisted by `BuildSystems`, `LocateModules`, `ModulePurpose`, `ModuleArchitecture`, `SBOMLocation`) and `filesystem_count_per_filetype_and_directory` (whitelisted by `ProgrammingLanguages`, `ModuleSubdirectories`). Five of those seven entries are loop tasks over all modules, and their prompts ask for a single wildcard (`pom.xml`) most of the time — exactly the single-element array that models serialise as a scalar. One malformed call loses that module's step, marks the task failed, and keeps its dependents out of the run.
2. **A hallucinated tool name ends the step.** The engine throws on an unknown tool name, although the model could correct itself if it were told which tools exist.
3. **The two error handlers disagree.** A tool that throws hands its message back to the model and the step continues; a tool whose *arguments* are unusable kills the step. The loop is also unbounded, so handing argument errors back to the model without a bound would let a stubborn model spin forever.

Text-level hardening is already in place and does not help: the parameter description says "Hand over an array, even if you call the method with only one wildcard!" and the prompt repeats it. The failure is a model serialisation habit, not a misunderstanding of the interface.

Measured tool usage, from the 343 step logs in `workspaces/` (6 projects): 275 steps use 1 tool call, 42 use 2, 8 use 3, 7 use 4, 2 use 6, 1 uses 24. Any bound on tool rounds must not reject those steps; a bound of 3 would have rejected 10 known-good steps.

## What Changes

- **A tool argument error is returned to the model as the tool result.** An argument that cannot be coerced no longer ends the step: the model receives the coercion message and may call the tool again with corrected arguments. This is the fix for issue #14, and it is the behaviour upstream recommends and already uses for its async paths.
- **A hallucinated tool name is returned to the model as the tool result**, naming the tool that was requested and the tools that exist, instead of throwing.
- **New `maxToolRoundTrips` configuration option**, `config.json` only, default 10, minimum 1: the maximum number of tool-call rounds the model may request within one step attempt. No CLI option, matching `retries`.
- **Exceeding the bound rejects the step** with a new failure reason, so the existing per-step attempt budget (`retries`) handles it in a fresh conversation with a repair hint naming the reason. No new retry machinery, no silent truncation.
- **The engine takes the tool error policy from the `ToolService`** — arguments handler, execution handler, round-trip bound and executor — instead of keeping private copies in `ChatExecutor`, so the framework defaults, the factory wiring and the loop cannot drift apart. The hallucinated-name strategy stays owned by the engine, because `ToolService` exposes it as a setter only: there is no getter to read it back from.
- **Reporting and logs**: an argument error handed back to the model, and a step rejected for exceeding the bound, are each reported through the existing channels (engine log and progress display), and the tool result the model receives stays visible in the per-attempt chat log.
- **`AGENTS.md`** updated where it currently states that tool errors fail a step immediately, plus the new configuration option and the round-trip bound.
- **BREAKING** (behaviour, not interface): a step that used to fail on its first uncoercible argument now spends model calls and can succeed; a step whose model requests more tool rounds than `maxToolRoundTrips` now fails, where its loop was previously unbounded.

## Capabilities

### New Capabilities

- `llm-tool-execution`: the engine's tool-call loop inside one task step — which tool errors are returned to the model, how a hallucinated tool name is answered, the bound on the model's tool rounds, and what happens when the bound is exceeded.

### Modified Capabilities

None. The new rejection reuses the existing rejection and attempt rules (`llm-response-retry`: only a conformant attempt is stored, an exhausted budget fails the step; `task-failure-handling`: a failed task does not unlock dependents and runs again on the next run). No requirement of those specs changes, so no delta file is needed.

Note for a later cleanup, not part of this change: `task-failure-handling` and `llm-response-schema-validation` still describe schema violations as diagnostic-only, which contradicts the implemented behaviour and `AGENTS.md`.

## Impact

- **Code**: `lc4j/ChatExecutor.java` (handlers, executor and bound read from `ToolService`; hallucinated-name strategy owned by the engine; bounded loop; new rejection), `lc4j/StepFailureReason.java` (new reason), `tools/ToolServiceFactory.java` (wiring of handlers and bound), `config/Config.java` (new option, default, dump), `src/main/resources/schema/config-file.json` (`maxToolRoundTrips`, `minimum: 1`).
- **Depends on** `upgrade-langchain4j-1210`: the argument error handler is `ToolArgumentsErrorHandler.sendExceptionMessageToLlm()`, which exists from 1.21.0.
- **Tests**: new tests that drive `ChatExecutor` with a canned model whose tool call carries a scalar for an array-typed parameter (expecting the error text to reach the model as a tool result and the step to keep running), a hallucinated tool name (expecting a tool result naming the available tools), and a model that exceeds the bound (expecting a rejected outcome); plus config tests for the new option's default and minimum.
- **Affected docs**: `AGENTS.md` (LLM execution, MCP tools, workspace configuration).
- **Not affected**: `analysis/software-architecture` (tasks, prompts, response schemas), DAG scheduling and tag resolution, `state.json` format, `analysis.json` format, the `pom.xml` dependency set beyond what `upgrade-langchain4j-1210` changes.

## Open Questions

- Should `maxToolRoundTrips` accept `0` as "unlimited" (today's behaviour) instead of rejecting values below 1? Current proposal: minimum 1, no unlimited mode.
- Should the text handed back to the model name the offending parameter and its expected JSON form (which needs the tool specification), or carry only the framework's coercion message? Current proposal: the framework message, which already names the class it could not construct.
- Does the bound count tool-call rounds (assistant turns) or individual tool calls? Current proposal: rounds; the measured logs show approximately one call per round.
- Is the bound per attempt or per step across attempts? Current proposal: per attempt, so each attempt of a step starts with a full budget.
- Should an argument error handed back to the model be visible in the TUI as well, or only in the engine log and the chat log? Current proposal: engine log and chat log, because the model continues working and the step is not failing.
