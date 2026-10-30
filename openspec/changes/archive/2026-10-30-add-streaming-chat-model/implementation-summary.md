# Implementation Summary — add-streaming-chat-model

## Task 2.3 — Builder parity (live endpoint)

Compared the wire requests of both transports against the vLLM endpoint
(`http://10.100.133.80:8001/v1`, model `qwen3.8-27b`) with `logRequests` enabled:

| parameter    | non-streaming | streaming |
|--------------|---------------|-----------|
| `model`      | `qwen3.8-27b` | `qwen3.8-27b` |
| `temperature`| `0.0`         | `0.0`     |
| `top_p`      | `0.9`         | `0.9`     |
| `max_tokens` | `65536`       | `65536`   |
| `stream`     | `false`       | `true`    |

The only wire difference is the required `stream` flag; with `nativeJSON`
enabled the streaming request additionally carries the same `response_format`
as the synchronous mode. The response contract (tool loop, JSON extraction,
schema validation, token usage, retry classification) is reached through the
same `ChatExecutor` code path in both modes, because the adapter presents a
plain `ChatModel`.

Unavoidable difference, noted in a code comment in `ChatModelFactory`: the
langchain4j 1.21.0 `OpenAiStreamingChatModel` builder has no `maxRetries`
option because the streaming client does not retry on its own; the engine
stays the single retry authority in both modes.

## Streaming run crashed on first attempt — root cause and fix

The first streaming integration run failed before reaching the endpoint:

```
java.lang.ClassCastException: class dev.langchain4j.model.chat.request.DefaultChatRequestParameters
    cannot be cast to class dev.langchain4j.model.openai.OpenAiChatRequestParameters
    at dev.langchain4j.model.openai.OpenAiStreamingChatModel.doChat(OpenAiStreamingChatModel.java:145)
    at com.framstag.llmaj.lc4j.StreamingChatModelAdapter.doChat(StreamingChatModelAdapter.java:52)
```

Root cause: the interface defaults of `ChatModel.chat(ChatRequest)` and
`StreamingChatModel.chat(request, options, handler)` merge
`defaultRequestParameters().overrideWith(request.parameters())` before
delegating to `doChat`. Both OpenAI models override
`defaultRequestParameters()` with the builder-built
`OpenAiChatRequestParameters`, whose `overrideWith`/`defaultedBy` always
return their own type; `OpenAiStreamingChatModel.doChat` then hard-casts the
merged parameters to that type (line 145). The engine builds its requests
with generic `ChatRequestParameters`, so the merge must run with the
provider's defaults as the receiver. The first adapter version neither
overrode `defaultRequestParameters()` (the interface default returns the
generic `DefaultChatRequestParameters.EMPTY`) nor went through `chat()`
(it called `streamingModel.doChat(request, handler)` directly), so the
generic parameters reached the hard cast.

Fix in `StreamingChatModelAdapter` (defense in depth; the double merge is
idempotent because both sides return `OpenAiChatRequestParameters` for any
input):

1. `defaultRequestParameters()` overrides the interface default and delegates
   to the wrapped streaming model's provider-specific defaults.
2. `doChat` delegates to `streamingModel.chat(request, handler)` so the
   merge of provider defaults with the request parameters runs, exactly as
   in the synchronous model.

Regression test:
`StreamingChatModelAdapterTest.theStreamingModelReceivesItsProviderSpecificMergedParameters`
asserts the request that reaches the model's `doChat` carries
`OpenAiChatRequestParameters` (fails against the first adapter version).

## Task 3.1 — streaming vs non-streaming, `cloud-strategy-compatibility-assessment`

Task step run twice against the vLLM endpoint in the
`cloud-sovereignty-analysis` jinni-workspace.

Non-streaming baseline (streaming disabled, the long-failing configuration):
three attempts, each rejected by the nginx front of the endpoint with
`504 Gateway Time-out` — the gateway's idle timeout expires while the
blocking request silently generates roughly 24k output tokens. The task step
ended FAILED without producing a response
(`/tmp/streaming-verify/nonstreaming/nonstreaming-console.log`).

Streaming run (`--streaming true` on the command line,
`Effective useStreaming: true (source: COMMAND_LINE)`), 13:27:56 to
13:48:05 (~20 minutes):

- Round 1 at 13:27:58 sent with `"stream": true`; the endpoint answered
  `200` with `content-type: text/event-stream` and the connection stayed fed
  for the whole generation — no gateway 504.
- The model's tool call (steampipe MCP) executed, its result returned to the
  model, round 2 sent at 13:34:49, again `200` event stream.
- The aggregated response was parsed and schema-validated through the
  unchanged task-step machinery; the final answer was written to
  `analysis.json` as the `cloudStrategyCompatibilityAssessment` property.
- Execution state: task `SUCCESSFUL`,
  tokenUsage `{ inputTokens: 24307, outputTokens: 24392, totalTokens: 48699 }`,
  `Token usage: IN 24307 OUT 24392 TOTAL 48699 (finish reason: STOP)`.
- No model errors, no client retries, no `ClassCastException`.

Contract equivalence: both modes emit the same interaction log lines (token
usage, finish reason), write the same chat log and state entries, and route
every failure through the same `ModelCallFailure`/`TaskStepRetrier`
classification — the adapter hides the transport entirely.

Source coverage: the successful run used the CLI source; the config-file
source is proven end-to-end by the task 3.2 run below
(`Effective useStreaming: true (source: CONFIG_FILE)`) and by
`ConfigUseStreamingTest`/`ConfigOverridesTest`. The real workspace
`config.json` was never modified; streaming was always activated per run.

## Task 3.2 — fail-fast for unsupported providers

Disposable workspace with `modelProvider: "ollama"` and
`useStreaming: true` in `config.json` (the real workspace config was never
touched). The run logged `Effective useStreaming: true (source: CONFIG_FILE)`
and then terminated at model construction, before the task manager was
initialized and before any task step could execute:

```
java.lang.IllegalStateException: Streaming is not supported for the ollama
provider; use the OpenAI provider or disable streaming
    at com.framstag.llmaj.lc4j.ChatModelFactory.getChatModel(ChatModelFactory.java:31)
    at com.framstag.llmaj.cli.AnalyseCmd.call(AnalyseCmd.java:186)
```

Exit code 1. Unit coverage: `ChatModelFactoryTest` asserts the startup error
for Ollama and LocalAI with streaming enabled.

## Task 3.3 — verification gate

- `npx --yes @fission-ai/openspec@1.14.3 validate --all --strict`:
  **55 passed, 0 failed**.
- `mvn -B verify` on this branch: **636 tests, 0 failures, 4 skipped**,
  plus the packaged-jar smoke test `JarSmokeIT` (6 tests green) — fully
  green on this fork and on the origin tree alike.

The first full gate run surfaced three test failures that all reproduce on
the untouched `HEAD` (`b955877`) and are unrelated to the streaming change;
all three were repaired as maintenance of stale test expectations:

1. `SBOMToolTest.correctlyReturnApplicationDependencies` — the test asserted
   a hardcoded dependency list, which is green on origin but red on this
   fork (whose `pom.xml` additionally declares
   `io.burt/jmespath-jackson@0.6.0`; baking the fork-only dependency into
   the expectation would make the change unmergeable to origin). The test
   now derives the expectation from `target/bom.json` — the SBOM the build
   actually generated — by reading it as raw JSON with Jackson (a parser
   the tool under test does not use) and collecting the refs the root
   component's `dependsOn` entry lists. A single version-agnostic anchor
   (the core `pkg:maven/dev.langchain4j/langchain4j@` prefix must be part
   of the selection) additionally fails if tool and reference collapse to
   the same wrong result. With the raw reader the expectation tracks the
   pom on any branch and the assertion is green everywhere.
2. `FileToolTest.GetAllFilesInDirWithSubdir` — the hardcoded snapshot of the
   test source tree predated `handlebars/helpers/CustomJSONHelpersIntegrationTest.java`
   and the three streaming test files added by this change. Snapshot updated;
   every referenced file exists on origin.
3. `StateTokensCmdTest.droppingATaskRemovesItsRecordedUsage` — the fixture's
   task name `ModulePurpose` collided with the repository's schema file
   `analysis/software-architecture/results/ModulePurpose.json`, whose path
   the `TaskManager` validation log writes to the captured stdout and thus
   leaked into the asserted report. Fixture task renamed to
   `DroppedModuleTask`.
