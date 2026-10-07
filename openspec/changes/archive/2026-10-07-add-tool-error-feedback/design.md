# Design

## Context

See `proposal.md` for motivation and `specs/llm-tool-execution/spec.md` for the requirements. The constraints that shape the approach:

- `ChatExecutor.executeMessages()` is one LLM conversation: it patches the user message with the schema description, runs the tool loop, writes the chat log, parses the payload and validates it. It is called from `AnalyseCmd.runStepWithRetries`, which builds `new ChatExecutor()` inside the attempt lambda, so one executor instance serves exactly one step attempt.
- The loop is `while (chatResponse.aiMessage().hasToolExecutionRequests())` with no bound.
- `ChatExecutor` keeps private copies of four things the `ToolService` also exposes: the arguments error handler (rethrows, ends the step), the execution error handler (returns the text, step continues), the hallucinated-tool-name strategy (`THROW_EXCEPTION`), and the executor service. It also has a private `executeWithErrorHandling`, which mirrors the public static `ToolService.executeWithErrorHandling`.
- `ToolService` (1.21.0) exposes getters for `argumentsErrorHandler()`, `executionErrorHandler()`, `maxToolCallingRoundTrips()` and `effectiveToolExecutor()`, plus `hasExplicitArgumentsErrorHandler()`. It has **no** getter for the hallucinated-tool-name strategy: setter only.
- The framework's own loop, when it exceeds the round-trip bound, throws `"Something is wrong, exceeded %s tool calling round trips (maxToolCallingRoundTrips)"`. The engine does not use that loop.
- `ToolArgumentsErrorHandler.sendExceptionMessageToLlm()` (1.21.0) returns the error text to the model; the sync default is still `failInvocation()`, which throws.
- The existing rejection machinery is `TaskStepOutcome` plus `TaskStepFailure` (`StepFailureReason`, bounded `repairHint()`, bounded `displayMessage()`), driven by `TaskStepRetrier` with the `retries` budget.
- Config plumbing: `Config` field with a built-in default, `src/main/resources/schema/config-file.json` for validation, `providedProperties` to tell file values from defaults. `retries` follows exactly this pattern and has no CLI option.
- Measured tool usage across 343 step logs in `workspaces/`: 1 round in 275 steps, 2 in 42, 3 in 8, 4 in 7, 6 in 2, 24 in 1.

## Goals / Non-Goals

**Goals:**

- Make every tool-call failure that the model could repair a recoverable, bounded, reported event inside the step instead of the end of the step.
- One authority for the tool policy: what the factory configures is what the loop enforces.
- Keep the engine out of serialization: the engine never rewrites tool arguments or supplies its own JSON codec.

**Non-Goals:**

- No engine-side coercion of tool arguments. The framework owns argument preparation; the engine only decides what happens when it fails. A scalar-for-array argument is reported, not silently accepted.
- No delegation of the whole tool loop to langchain4j's `executeInferenceAndToolsLoop`: the engine's loop interleaves per-provider request parameters, progress reporting and the final JSON-only request.
- No change to the DAG, the analysis domain, `state.json`, `analysis.json`, or the `retries` budget semantics.
- No new TUI element. Rejections keep their existing reporting.

## Decisions

### Decision 1: Return what the framework already offers, instead of composing messages

Argument errors are handed back with `ToolArgumentsErrorHandler.sendExceptionMessageToLlm()`, wired once in `ToolServiceFactory`, instead of a hand-written lambda in `ChatExecutor`.

Alternatives considered:

- **Engine-composed text naming the parameter and its expected JSON form.** Needs the tool specification for the requested tool, and duplicates text upstream owns. Deferred, see Open Questions.
- **Keep the rethrow and rely on the step retry.** Rejected: it spends a whole conversation on an error the current conversation can fix, which is the outcome this change exists to remove.

### Decision 2: The policy lives in the `ToolService`, the loop reads it

`ToolServiceFactory` sets the arguments handler, the execution handler and the round-trip bound on the `ToolService`. `ChatExecutor` reads them per attempt from `executionContext.getToolService()`.

Alternatives considered:

- **Keep the private handler fields in `ChatExecutor` and read `Config` directly for the bound.** Rejected: two authorities for each value, and the framework's own defaults would be silently overridden by copies.
- **Delegate the handlers to `ToolService.executeWithErrorHandling(...)`.** Preferable for duplication, but only adopted if the public static keeps its signature after the upgrade; otherwise the private copy stays and the reason is recorded.

The hallucinated-tool-name strategy is the exception: no getter exists, so the engine owns it. It produces a bounded tool result naming the requested tool and the available tools.

Adopted for the two handlers: the private copy was deleted and the engine now calls the public static `ToolService.executeWithErrorHandling(...)`, whose signature is unchanged in 1.21.0. The existing outcome tests pass unchanged, and the error result keeps `isError` set, which the engine uses for its reporting.

### Decision 3: The bound is enforced by the engine and rejects the step

The engine counts tool rounds per attempt; when the count reaches `maxToolRoundTrips`, the tools of the further round are not executed and the attempt ends as a rejected step with a new `StepFailureReason`.

Alternatives considered:

- **Throw, as the framework's own loop does.** Rejected: a self-inflicted, expected outcome as an exception bypasses the attempt budget and contradicts the change's goal.
- **Silently stop the tool phase and ask for the final answer.** Rejected: it hides a runaway model and would accept an answer produced from an incomplete tool phase.

The chat log is written before the rejection is returned, so the transcript of the runaway attempt survives — the same rule the per-attempt logs already follow.

### Decision 4: Count rounds, per attempt

A round is one model response requesting tool executions and the answers to them. Per attempt, not per step: each attempt starts a fresh conversation, so it starts with a fresh budget.

Alternatives considered: counting individual tool calls (rejected: the bound exists to limit conversation growth, which rounds drive), counting per step across attempts (rejected: it would make the effective bound depend on the `retries` budget).

### Decision 5: `maxToolRoundTrips` is a configuration value with a conservative default

`config.json` only, default 10, `minimum: 1` in the config schema, no CLI option — the same shape as `retries`.

Alternatives considered:

- **Framework default of 100.** Rejected as an engine default: the bound exists to catch a runaway model, and 100 rounds of a growing conversation is not a guard.
- **3, as first proposed.** Rejected on the measured data: 10 of 343 known-good steps used more than 3 rounds, including one that used 24 and still produced a valid answer. A default of 10 rejects that single outlier and nothing else observed.
- **0 as "unlimited".** Rejected for now: an unbounded loop is what this change is fixing. Recording it as an open question.

Set on the `ToolService` by the factory and read back by the loop, so the value the framework would use and the value the engine enforces cannot diverge.

### Decision 6: Reporting follows the existing channels

An argument or execution error returned to the model is one WARN engine log record naming the tool: the step is not failing, so it must not consume a retry line or a TUI error. A step rejected for exceeding the bound goes through the existing rejected-attempt reporting unchanged, because it is a rejection like any other.

## Verification findings

- **Live run** (`workspaces/spring-petclinic`, cloud-served Ollama backend): `ProgrammingLanguages` and `ModulePurpose` completed successfully in one run each, with no retry, no rejection and no tool error record; the chat log shows the model sent `"wildcards":["*.java"]`, a proper array. The scalar-for-array serialisation of issue #14 comes from the vLLM-served model in the report and did not reproduce on this backend, so the recovery path was not observed live. Its evidence is `lc4j/ChatExecutorToolErrorTest`, which reproduces the exact framework message (`Cannot construct instance of java.util.ArrayList ... no String-argument constructor/factory method to deserialize from String value ('pom.xml')`), asserts that the tool does not run, that the message reaches the model as the tool result, that the corrected call is then executed, and that a raising tool and an unknown tool name behave the same way.
- **Bound rejection live** (`workspaces/spring-petclinic` with `maxToolRoundTrips` set to 1, `LocateModules` dropped from the state first): the run rejected attempt 1, because the task needs two tool rounds. Observed: engine log `Task 'LocateModules' reached the tool round bound of 1 and was rejected`, the retry line `attempt 1/3 failed (tool round bound exceeded: the model requested more than 1 tool round(s) in one attempt), retrying`, a second attempt that stayed inside the bound and was accepted, `logs/LocateModules.log` holding both the executed and the refused tool call, and `logs/LocateModules.attempt2.log` holding the recovered attempt. `state dump` reports `[x] LocateModules` because the retry recovered the step; the leg where every attempt is rejected, so the task ends failed and runs again on the next run, is covered by `cli/AnalyseCmdRetryTest.exhaustedRetriesFailTheTaskAndStoreNothing` and `lc4j/ChatExecutorToolRoundBoundTest`.
- Unit tests after the change: 240, plus 6 packaged-jar smoke tests, all green.

## Risks / Trade-offs

- **Default 10 rejects the one measured 24-round step** -> accepted and recorded: the value is configurable, the rejection is reported and retried, and only 1 of 343 observed steps is affected.
- **Every handed-back error costs a full round trip**, because the whole conversation is resent; the measured 24-round step used about 79k input tokens -> mitigation: the bound, the WARN record, and the tool result staying visible in the chat log.
- **A model that keeps repeating the bad call** -> bounded by `maxToolRoundTrips` per attempt and by the `retries` budget per step; worst case is `retries` x bound rounds.
- **Error text reaches the model verbatim** -> the text describes the model's own arguments; the engine's tool parameter types are plain types, so no application data is exposed. Upstream warns about custom deserializers, which this engine does not have.
- **The available-tools list in a hallucination answer can be long** -> the tool result text is bounded, and the count of registered tools is small.
- **A throwing arguments handler would restore the old behaviour** (the rethrow path still exists for other exceptions) -> a test pins that an uncoercible argument produces a tool result and not a step failure.
- **Reliance on `ToolService` getters ties the engine to upstream API** -> the upgrade change is the compile gate; the two changes are sequenced so a signature change surfaces in the first one.
- **The bound is a new way for a previously successful step to fail** -> it is reported, retried within the existing budget, and the failure names the bound so the operator can raise it.

## Migration Plan

1. Land `upgrade-langchain4j-1210` first; this change compiles against 1.21.0.
2. Add the failure reason and the config option (schema, default, dump).
3. Rewire `ToolServiceFactory` and `ChatExecutor`; bound the loop; reject on excess.
4. Tests: scalar argument for an array-typed parameter, hallucinated tool name, bound exceeded, config default and minimum.
5. Update `AGENTS.md` where it describes tool errors as fatal.
6. Live run against a real backend that reproduces the scalar-for-array habit, if one is available; otherwise the canned-model test is the evidence and the change records that the live case stayed unverified.

Rollback: revert the commit. `config.json` files that already carry `maxToolRoundTrips` stay valid because the config schema does not forbid additional top-level properties.

## Open Questions

- Whether the argument error text should name the offending parameter and the expected JSON form, which needs the tool specification of the requested tool, instead of the framework's message. Observed in the test: the framework message names the type it could not construct and the value the model supplied (`Cannot construct instance of java.util.ArrayList ... from String value ('pom.xml')`), but not the parameter name; an engine-composed text is a later enhancement, not part of this change.
- Whether the available-tool list in the answer to a hallucinated tool name should be filtered to the tools the task is allowed to use, rather than every registered tool.
