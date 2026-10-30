## Why

The engine only ever sends non-streaming chat requests — the synchronous `OpenAiChatModel` client implicitly transmits `"stream": false`, and the whole response must arrive in one blocking transfer. Long-running task calls with large prompts and a high `maximumTokens` then exceed intermediary gateway timeouts (nginx answers with `504 Gateway Time-out`), surfacing as retriable model errors even though the backend is still working. Streaming keeps the HTTP connection continuously active with incremental data, which avoids idle-timeout failures for OpenAI-compatible endpoints.

## What Changes

- New optional `useStreaming` setting (built-in default `false`): when enabled, model calls against OpenAI-compatible endpoints use the langchain4j `OpenAiStreamingChatModel` (wire format `"stream": true`), and the streamed chunks are aggregated back into the same final `ChatResponse` the engine already consumes.
- No observable behavior change while streaming: the tool-call loop, JSON payload parsing, schema validation, retriable-error retry-with-repair-hint flow, per-round console/file logging, and per-step token-usage recording all behave exactly as in the non-streaming mode.
- The synchronous, non-streaming mode remains the default and stays fully supported for all three model providers (OpenAI, Ollama, LocalAI).
- Enabling `useStreaming` with the Ollama or LocalAI provider fails the run at startup with a clear error message (streaming is only implemented for the OpenAI provider in this change).

## Capabilities

### New Capabilities
- `streaming-chat-model`: Opt-in streaming transport for OpenAI-compatible model calls, aggregated transparently into the engine's existing synchronous response contract.

### Modified Capabilities
- `cli-config-precedence`: the new `useStreaming` setting becomes an overridable option resolved in the established order (CLI option, then `config.json`, then built-in default) and is added to the overridable-option inventory.

## Impact

- `ChatModelFactory`: constructs `OpenAiStreamingChatModel` (same timeout, maxRetries, temperature, topP, maxTokens, logging/listener builder chain as today) when streaming is enabled for the OpenAI provider.
- `ChatExecutionContext` / `AnalyseCmd`: carry the streaming client type to the executor.
- `ChatExecutor`: the three model call sites bridge the async `StreamingChatModel.chat(request, handler)` API to a blocking `CompletableFuture`, preserving the method's synchronous contract so `TaskStepRetrier` and the DAG orchestrator remain untouched.
- `Config`, `ConfigOverrides`, CLI: new `useStreaming` flag with precedence handling and effective-value logging.
- No dependency version bump: langchain4j 1.21.0 (already pinned) provides `OpenAiStreamingChatModel` and `StreamingChatResponseHandler`.
- Workspaces that do not opt in are unaffected.
