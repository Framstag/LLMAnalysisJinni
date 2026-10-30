## Context

All model invocations go through one synchronous entry point per step: `ChatExecutor.callModel()` (`ChatExecutor.java:584`) executes `executionContext.getChatModel().chat(request)` and converts any `RuntimeException` into `ModelCallFailure`. The three call sites in `executeMessages()` (initial call, tool-loop call, final call) all funnel into this single helper. The client type is fixed at startup: `AnalyseCmd` line 182 calls `ChatModelFactory.getChatModel(config)`, which always returns a langchain4j `ChatModel` built synchronously per provider, and the instance is passed through `ChatExecutionContext` to the executor. Langchain4j 1.21.0 is already pinned and provides `OpenAiStreamingChatModel` plus the `StreamingChatResponseHandler` callback API — no dependency change is needed.

Retry authority sits in `TaskStepRetrier`, which classifies a `ModelCallFailure` as retriable when its cause chain contains a `RetriableException`. See proposal.md for the motivation (504s on gateway idle timeout).

## Goals / Non-Goals

**Goals:**
- Opt-in streaming transport for the OpenAI-compatible provider, enabled via configuration, defaulting to off.
- Keep the observable task-step contract (tool loop, payload parsing, schema validation, retries, logging, token usage) byte-for-byte behaviorally identical to the non-streaming mode.
- Keep the change surface minimal: no restructuring of `ChatExecutor`, `ChatExecutionContext`, `TaskStepRetrier`, or the DAG orchestrator.

**Non-Goals:**
- Streaming support for Ollama and LocalAI (fail-fast error instead).
- Per-task streaming toggles from `tasks.yaml` — this is an engine-level transport setting.
- Live per-token console rendering of streamed chunks (interaction logging stays as it is: one complete message per round).
- Changing gateway/nginx timeouts (out of scope for the engine; complementary operational step).

## Decisions

### D1: Bridge async to sync via a `ChatModel` adapter, not at the call sites

Introduce a new class `StreamingChatModelAdapter` implementing the langchain4j `ChatModel` interface. It wraps an `OpenAiStreamingChatModel`; its `chat(ChatRequest)` invokes `streamingModel.chat(request, handler)` where a `StreamingChatResponseHandler` completes a `CompletableFuture<ChatResponse>` on `onCompleteResponse` and completes it exceptionally on `onError`; the adapter then blocks on the future and returns the `ChatResponse`. The original error (e.g. `RetriableException`) is surfaced as the rethrown cause, so the existing `ModelCallFailure` wrapping in `callModel()` and the `cause instanceof RetriableException` check in `TaskStepRetrier` work without modification.

Rationale: because every call site already funnels through the single `callModel()` helper behind the `ChatModel` interface, an adapter keeps the change to one new class instead of touching `ChatExecutor`'s three call sites, the tool-loop control flow, `ChatExecutionContext`'s constructor and all its call sites, and `AnalyseCmd`'s model plumbing.

Alternatives considered:
- *Replace the `ChatModel` field with a `StreamingChatModel` and adapt the three call sites* — rejected: widens the change into the executor's control flow and exception handling for no behavioral benefit.
- *Rewrite `executeMessages`/the orchestrator to be fully async/callback-based* — rejected: changes the thread-pool/DAG concurrency model and the retry loop; far higher risk for no user-visible benefit.
- *A per-provider strategy object selected per request* — rejected: streaming is a run-level setting, not a per-request one.

### D2: Factory decides, and fails fast

`ChatModelFactory.getChatModel(config)` stays the single construction point with the unchanged signature. When `config.isUseStreaming()` is true:
- provider `OPENAI` → build `OpenAiStreamingChatModel` with the same builder chain as today (model name, base URL, API key, timeout, `maxRetries(0)`, temperature `0.0`, topP `0.9`, thinking settings, `logRequests`/`logResponses`, `maxTokens`, and the `ChatListener` when logging is active) and wrap it in `StreamingChatModelAdapter`;
- any other provider → throw a clear error at startup naming the provider and stating that streaming is not supported for it, so the run fails before any task step executes (per spec).

The non-streaming branches remain exactly as they are, so the default behavior is untouched.

Rationale: keeping the decision in the factory preserves the existing "one place builds the client" structure and gives the required fail-fast semantics without wiring validation elsewhere.

Alternative considered: *detect the missing provider support lazily on first model call* — rejected: the spec requires the run to fail before any step executes.

### D3: Streaming is a run-level boolean with existing config precedence

Add `useStreaming` (boolean, built-in default `false`) to `Config`, wired through the established precedence mechanism: new nullable `Boolean` component in `ConfigOverrides` (absent vs. default-valued distinction via `parseResult.hasMatchedOption(...)`), a new entry in `applyTo` and in `resolutions()` so the effective value and its source are logged at run start, and a new CLI option `--streaming` on the `analyse` command (`arity = "1"` so `--streaming false` explicitly overrides a `true` stored in `config.json`). `WorkspaceInitCmd`/config-file plumbing works through `config.json` as with the other boolean settings.

Rationale: follows the `cli-config-precedence` contract already in force; the spec delta for that capability is satisfied by adding the setting to the overridable inventory, with no other change to that capability.

### D4: No partial-chunk side effects

The handler ignores `onPartialResponse` except for keeping the future pending; there is no console echo, no partial logging, and no early JSON parsing. Only the complete response delivered in `onCompleteResponse` (final text and the provider-reported `TokenUsage`) is consumed.

Rationale: guarantees the "logging and token usage unchanged" spec requirements with zero changes to `ChatExecutor`, `ResponsePayloadParser`, or the token-usage recording code.

## Risks / Trade-offs

- [Streaming quirks of specific OpenAI-compatible backends] (e.g. known vLLM tool-argument serialization issues; some backends emit token usage only in the final chunk) → Mitigation: default off, per-workspace opt-in; can be disabled per workspace by setting `useStreaming: false`; verify token usage against the same endpoint before relying on it for costing.
- [Null/absent token usage in streaming mode could break per-step recording] → Mitigation: verify during implementation that `ChatResponse.metadata().tokenUsage()` null-safety behaves like the current code's handling of missing usage; if the adapter observes null usage where sync mode reports values, surface it in the run log rather than failing the step.
- [The bridge could block indefinitely if a callback never fires] → Mitigation: the client's configured HTTP timeout (`config.getRequestTimeout()`) bounds the underlying call; additionally block with `future.get(timeout, MINUTES)` using that value so a stuck stream degrades into a normal model-call failure instead of a hung step.
- [Streaming does not help if the gateway enforces a total-duration cap (not just an idle timeout)] → Trade-off: this change removes the idle-timeout failure mode (the observed nginx 504 while the backend is still generating); total-duration caps must be addressed separately in proxy configuration or by lowering `maximumTokens`.
- [Builder parity between the sync and streaming OpenAI clients] (e.g. `sendThinking`/`returnThinking` availability) → Mitigation: implementer verifies each existing builder option has a streaming equivalent and uses the closest behavioral setting where one lacks an exact counterpart; any drop is noted in the implementation summary.

## Migration Plan

1. Ship with `useStreaming` defaulting to `false` — existing workspaces and runs are unaffected; no data migration, no state changes.
2. Validate on the affected workspace/vLLM endpoint: run a task with streaming on and off, compare task output, retriable-error behavior, interaction logs, and recorded token usage.
3. Enable `useStreaming: true` (or `--streaming true`) for the workspace hitting gateway timeouts, in parallel with confirming the proxy fronts the endpoint with streaming-compatible (non-buffering) configuration.
4. Rollback: set the option back to `false` (or omit it); no persistence or state to migrate.

## Open Questions

- Whether to extend streaming to Ollama/LocalAI in a follow-up once the OpenAI path is proven (the adapter design leaves room for sibling adapters without further interface changes).
