# Tasks

## 1. Create ChatLogger class

Create a new `ChatLogger` class in `lc4j` package with the two logging methods extracted from `ChatExecutor`.

- [x] Create `ChatLogger.java` with `logProgressive(messages, showSystem)` instance method and `consoleShownIndex` field
- [x] Add `writeLogFile(workspacePath, taskId, loopIndex, messages, tokenUsage)` method
- [x] Wire `ChatLogger` into `ChatExecutor` as a field, initialized in constructor
- [x] Remove `consoleShownIndex`, `logProgressive()`, `writeLogFile()` from `ChatExecutor`
- [x] Redirect all calls in `executeMessages()` to `chatLogger.logProgressive()` / `chatLogger.writeLogFile()`
- [x] Clean up unused imports in `ChatExecutor`

**Files:**
- `src/main/java/com/framstag/llmaj/lc4j/ChatLogger.java` **NEW**
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java`

## 2. Add file logging to ChatExecutor

Add a method that writes the full conversation to a log file after all tool-call rounds complete.

- [x] Add `private void writeLogFile(Path logsDir, String taskId, Integer loopIndex, List<ChatMessage> messages, TokenUsage tokenUsage)` method
- [x] Determine log file path:
  - Loop task: `<workspace>/logs/<taskId>_<loopIndex>.log`
  - Non-loop: `<workspace>/logs/<taskId>.log`
- [x] Create `logs/` directory if not exists
- [x] Overwrite: if file exists, overwrite without warning
- [x] Write each message with type label (System, User, AI, ToolResult), full body
- [x] Write thinking trace (if present) as separate section under AI message
- [x] Write final token usage line: `Token usage: IN <n> / OUT <n> / TOTAL <n>`
- [x] Call `writeLogFile()` at end of `executeMessages()`, after all rounds complete, before returning result

**File format:**
```
=== Execution: <taskId>[_<index>] (<timestamp>) ===

── Message 1 (System) ──
<full text>

── Message 2 (User) ──
<full text>

── Message 3 (AI) ──
<response text>
→ Thinking: <thinking trace>   (if present)

── Message 4 (ToolResult) ──
Tool: <name>
<result>

── Token usage: IN 1500 / OUT 342 / TOTAL 1842 ──
```

**Params needed:** `executeMessages()` must receive or derive `taskId` and `loopIndex` from context.

**Files:**
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java`
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutionContext.java` — may need taskId/loopIndex added
- `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java` — pass taskId and optional loopIndex to context

## 3. Remove ChatListener from default model builder

- [x] In `ChatModelFactory.getChatModel()`, remove `.listeners(List.of(new ChatListener()))` from OllamaChatModel builder
- [x] Same for `OpenAiChatModel` builder
- [x] Keep `.listeners()` call only when `config.isLogRequests()` or `config.isLogResponses()` is true (debug mode)
- [x] In debug mode, pass a simplified listener that logs at TRACE level (not INFO)

**Files:**
- `src/main/java/com/framstag/llmaj/lc4j/ChatModelFactory.java`
- `src/main/java/com/framstag/llmaj/lc4j/ChatListener.java`

## 4. Clean up ChatListener

- [x] Remove `ChatListener` class or reduce to a minimal debug helper (only for `--log-request`/`--log-response` flags)
- [x] If kept, ensure it logs at TRACE level (not INFO), since console output is now handled by ChatExecutor
- [x] Update logback.xml — remove or downgrade the `ChatListener` logger config

**Files:**
- `src/main/java/com/framstag/llmaj/lc4j/ChatListener.java`
- `src/main/resources/logback.xml`

## 5. Update tests

- [x] Verify existing tests still pass (ChatExecutor tests, if any)
- [x] Add unit test for `logProgressive()` — verify that calling it twice with the same messages list only logs on first call
- [x] Add unit test for file logging — verify file content contains all messages, thinking traces, and token usage

**Files:**
- `src/test/java/com/framstag/llmaj/lc4j/` — new test file

## 6. Add `--execution-trace` CLI flag to analyse command

Add a `--execution-trace` flag on `analyse` that controls whether progressive chat messages appear on console. Default: `true`. File logging is independent and always-on.

- [x] Add `@Option(names={"--execution-trace"}, defaultValue = "true", description = "Show chat execution trace on console") boolean executionTrace;` to `AnalyseCmd`
- [x] Pass `executionTrace` setting through `Config` or `ChatExecutionContext` to `ChatExecutor.executeMessages()`
- [x] Guard console logging in `ChatExecutor`: only call `logProgressive()` when the flag is set
- [x] File logging (`writeLogFile()`) always runs — no guard needed
- [x] Thread safety: each worker thread gets its own `Executor` with its own `executionTrace` flag (no shared state)

**Files:**
- `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java`
- `src/main/java/com/framstag/llmaj/config/Config.java`
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java`
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutionContext.java`

## 8. Add system message filter for console output

System messages are excluded from console by default. Add `--execution-trace-system` flag to enable them.

- [x] Add `executionTraceSystem` field to `Config` (default `false`) with getter/setter
- [x] Add `@Option(names={"--execution-trace-system"}, defaultValue = "false", description = "Show system messages in console execution trace") boolean executionTraceSystem;` to `AnalyseCmd`
- [x] Pass `executionTraceSystem` through to `Config`
- [x] In `ChatExecutor.logProgressive()`, add `boolean showSystem` parameter. Skip `SYSTEM` messages when `showSystem` is `false`
- [x] Update all `logProgressive()` calls in `executeMessages()` to pass `config.isExecutionTraceSystem()`

**Files:**
- `src/main/java/com/framstag/llmaj/config/Config.java`
- `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java`
- `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java`
- `src/test/java/com/framstag/llmaj/lc4j/ChatExecutionLoggingTest.java`

## 7. Manual verification

- [ ] Run `workspace init` and `analyse` on a small project
- [ ] Verify console shows each message exactly once per execution (no gaps, no duplication)
- [ ] Run with `--execution-trace=false` — verify console is clean (no chat messages)
- [ ] Run with `--execution-trace=true` (default) — verify log file written even when console is clean
- [ ] Run same task twice — verify log files overwrite on second run
- [ ] Run parallel loop analysis — verify each worker's log is separate and correct
- [ ] Run with `--log-request` / `--log-response` flags and verify debug-level output still works
