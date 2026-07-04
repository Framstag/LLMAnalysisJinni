## Context

ChatExecutor.executeMessages() currently handles a multi-round conversation (initial LLM call → tool calls → intermediate LLM calls → final LLM call). Each round sends the full accumulated `chatMemory.messages()` to the LLM. Logging is delegated to a LangChain4j `ChatModelListener` that fires `onRequest`/`onResponse` per round.

The listener receives the full `ChatRequest.messages()` on each call but currently logs only `messages().getLast()`, producing gaps. It is shared across parallel loop workers (same `ChatModel` instance), so adding per-execution state requires ThreadLocal. The listener has no access to the final aggregated state or thinking traces.

ChatExecutor already owns `chatMemory` and the aggregate `TokenUsage`. Moving logging there eliminates the listener's purpose.

```
BEFORE:

AnalyseCmd (parallel loop)
  └── model.chat(request)
        └── ChatListener.onRequest  (per round, shared across threads)
        └── ChatListener.onResponse (per round)

AFTER:

AnalyseCmd (parallel loop)
  └── new ChatExecutor().executeMessages()
        ├── console: logProgressive(messages)  (per round)
        ├── model.chat(request)
        └── file: writeLog(messages, tokens, thinking)  (once, after all rounds)
```

## Goals / Non-Goals

**Goals:**
- Console shows each message once, in order, with no redundancy across tool-call rounds
- File captures complete multi-round conversation with thinking traces and token usage
- Thread safety by instance isolation (no shared state)
- Streaming-ready — request messages logged before LLM call, file written after completion
- ChatListener either removed or reduced to debug-only helper

**Non-Goals:**
- Implementing streaming itself (future work)
- Changing tool execution or task scheduling logic
- Adding structured logging (JSON logs, etc.)
- Replacing SLF4J

## Decisions

### Decision 1: Direct logging in ChatExecutor over listener

The `ChatModelListener` API provides no information that ChatExecutor doesn't already have access to. ChatExecutor constructs the `ChatRequest`, owns `chatMemory`, and receives the `ChatResponse`. Keeping the listener adds indirection without benefit.

**Alternatives considered:**
- **Enhanced listener with state** — requires ThreadLocal for thread safety, still can't capture aggregate view across all rounds in one place
- **Two-tier listener + executor** — unnecessary split, console and file can both live in executor with the same state

### Decision 2: Instance field `consoleShownIndex` for de-duplication

Each `ChatExecutor` instance tracks how many messages it has already logged via a simple `int` field. Before each `chat()` call, it iterates from the last shown index to the current messages size.

```java
private int consoleShownIndex = 0;

private void logProgressive(Logger log, List<ChatMessage> messages) {
    for (int i = consoleShownIndex; i < messages.size(); i++) {
        ChatMessage msg = messages.get(i);
        log.info(shortFormat(msg));
    }
    consoleShownIndex = messages.size();
}
```

**Alternatives considered:**
- **Always log full list** — redundant, noisy
- **Log only last message** — current behavior, lossy
- **Diff against previous list** — unnecessary complexity, `consoleShownIndex` is sufficient since messages only ever grow

### Decision 3: File log path = `<workspace>/logs/<taskId>[_<loopIndex>].log`

Deterministic path lets users find logs by task. The loop index suffix distinguishes parallel iterations.

Non-loop tasks: `logs/MyTaskId.log`
Loop tasks: `logs/MyTaskId_0.log`, `logs/MyTaskId_1.log`, ...

**Alternatives considered:**
- **Timestamped names** — harder to correlate with task output
- **Single combined file** — would need synchronization or separate sections, more complex

### Decision 4: ChatListener retained only for debug flags

LangChain4j's built-in `.logRequests(true)` / `.logResponses(true)` on the ChatModel builder produce very low-level HTTP wire logs. The `ChatListener` can serve as a lightweight debug logger when these flags are on, but is NOT the primary mechanism.

Remove listener from default model builder. Add it only when `--log-request` or `--log-response` CLI flags are active.

### Decision 5: Console output uses SLF4J `logger.info`

Existing logging convention. No new logging framework. The short one-line-per-message format keeps console readable.

### Decision 6: Console shows thinking traces

When the model returns thinking content (`.returnThinking(true)` activated in future), the console SHALL display it progressively. Format on console:

```
< response text
> Thinking: <thinking trace>
```

This applies both to synchronous mode (log on `onResponse`) and streaming mode (log per token). Currently thinking is hardcoded off; enabling it is a separate change.

### Decision 7: `--execution-trace` CLI flag toggles console, file always-on

Console output of progressive chat messages is controlled by `--execution-trace` flag on `analyse` command. Default: `true` (console shows messages). Set `--execution-trace=false` to suppress console chat output while still writing log files.

System messages are excluded from console by default (they clutter output). A separate flag `--execution-trace-system` enables system message display on console. File log always includes system messages regardless of flags.

File logging is always-on. Every execution writes a log file. The user gets a complete record regardless of console verbosity.

### Decision 8: Logs overwrite on re-execution

Running the same task again overwrites the existing log file. No versioned filenames or append logic. Rationale: each run is a fresh capture; old logs are stale. Users who want to keep old logs can move them manually.

## Risks / Trade-offs

[Console interleaving under parallelism] → Already exists today. Multiple threads writing to SLF4J at the same time interleave. Mitigation: each message is a single `logger.info()` call, so at most one line interleaves. Acceptable for console.

[File I/O in executor thread] → Writing a log file after execution adds latency. File is small (KB range for typical conversation). Negligible compared to LLM call time (seconds).

[Need to remember dropped ChatListener] → If someone adds a new framework-based feature that relies on `ChatModelListener`, they might expect it to be configured. Mitigation: keep the listener class but remove it from default builder, or leave a comment in `ChatModelFactory`.

[Streaming readiness is untested until streaming is actually implemented] → The design is correct in principle (log before call, log after completion) but will only be verified when streaming work begins. Acceptable — no regression risk.

## Resolved Questions

- **Log file lifecycle**: Overwritten on re-execution. No auto-cleanup.
- **File format**: Plain text. No Markdown or JSON.
- **Console thinking**: Shown both on console and in file log. Streaming mode: shown progressively per token.
- **Console trace on/off**: CLI flag `--execution-trace` on `analyse` command. Default `true`. File logging always-on. System messages filtered from console by default; `--execution-trace-system` enables them.
