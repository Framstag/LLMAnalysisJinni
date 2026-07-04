## Why

Current LLM interaction logging depends on a LangChain4j `ChatModelListener` that only sees the last message per request round. This produces gaps (system prompt, AI messages with tool calls, thinking traces are silently dropped) and ties logging to a framework callback that provides no unique information over what `ChatExecutor` already owns. Parallel loop execution (already deployed) shares a single listener across threads, making per-execution state tracking fragile.

Direct logging inside `ChatExecutor` gives full control over console output (progressive, de-duplicated per round-trip), complete file output (every message, thinking, token usage), and thread safety by instance isolation — each `ChatExecutor` instance serves one execution. It also prepares for streaming, where the listener's `onRequest`/`onResponse` model can't capture incremental tokens.

## What Changes

- **Remove `ChatListener`** as the primary logging mechanism. The class stays only as an optional debug aid for `--log-request`/`--log-response` flags.
- **Add progressive console logging** inside `ChatExecutor.executeMessages()`. Each round-trip logs only the NEW messages since the last round. No redundancy, no gaps.
- **Add file logging** inside `ChatExecutor.executeMessages()`. After all tool-call rounds complete, write the full conversation (every message, thinking traces, token usage) to a dedicated log file.
- **Thread safety** by instance isolation — each `ChatExecutor` is `new` per call, its logging state is per-instance. No shared state between parallel workers.
- **Streaming foundation** — console logging logs request messages before the LLM call starts. A streaming handler (future) can show tokens/thinking progressively. File logging writes after completion regardless of sync/stream mode.

## Capabilities

### New Capabilities

- `llm-interaction-logger`: Console + file logging of LLM interactions. Console shows progressive per-round messages (each message once, in order, no redundancy). File contains the complete multi-round conversation with message type labels, thinking traces (when available), and token usage summary.

### Modified Capabilities

- *None.* No existing spec-level behavior changes. Existing `llm-response-schema-validation`, `loop-parallelism`, and other specs are unaffected.

## Impact

- `src/main/java/com/framstag/llmaj/lc4j/ChatListener.java` — stripped to debug-only or removed.
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java` — gains console logging calls and file logging call.
- `src/main/java/com/framstag/llmaj/lc4j/ChatModelFactory.java` — listener registration may be removed or guarded by debug flag.
- `src/main/resources/logback.xml` — logger config for `ChatListener` may be removed.
- No new dependencies. Uses SLF4J for console, `java.nio.file` for file output.