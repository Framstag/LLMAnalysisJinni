# Design

## Context

See `proposal.md` for motivation. The constraints that shape the approach:

- `ChatExecutor.executeMessages()` is one LLM conversation: it patches the user message with the schema description, runs tool rounds, writes the chat log, parses the payload, and currently logs schema violations as a warning while returning the payload. It is called from two places in `AnalyseCmd.buildTaskRunner`: once per loop index worker and once for a non-loop task.
- `executeMessages()` mutates the caller's message list (it removes the last `UserMessage` and re-adds it patched), so a fresh conversation per attempt has to rebuild that list.
- `ChatLogger.writeLogFile()` writes a fixed file name and overwrites it.
- `ProgressCallback` is implemented by `ProgressDisplay` (TUI), `SimpleOutput` (piped/CI) and a no-op instance; `DisplayManager` routes to them.
- langchain4j already classifies model errors: `RetriableException` (with `TimeoutException`, `RateLimitException`, `InternalServerException`) versus `NonRetriableException` (with `InvalidRequestException`, `ModelNotFoundException`, `AuthenticationException`, `UnresolvedModelServerException`), plus `HttpException`, `UnsupportedFeatureException` and `ToolExecutionException` outside both trees.
- Task status is owned by `TaskManager` (`markTaskAsSuccessful`, `markTaskAsFailed`, `markIndexSuccessful`) and written by `AnalyseCmd`, not by `ChatExecutor`.

## Goals / Non-Goals

**Goals:**

- One retry policy object used by both call sites, so loop indices and non-loop tasks behave identically.
- A step outcome type that makes "accepted" versus "retryable failure" versus "fatal failure" explicit at the call site.
- Retry reporting that works in all three display modes without new threads.

**Non-Goals:**

- No CLI option for the attempt budget. Configuration file only.
- No delay or backoff between attempts.
- No change to the DAG scheduler, the loop parallelism, `state.json`, or the analysis YAML, prompts and schemas.
- No change to how a retryable failure is distinguished from a fatal one for tool calls: a tool failure that aborts the step stays fatal.

## Decisions

### Decision 1: The retry policy lives outside `ChatExecutor`

A new `lc4j` type (working name `TaskStepRetrier`) owns the attempt loop. `AnalyseCmd` calls it once per step and passes a function that performs one attempt.

Alternatives considered:

- **Retry inside `ChatExecutor`.** Rejected: it would need to rebuild the conversation, own the attempt counter, and decide publication, all inside a class that already handles memory, tools, logging and validation. It would also make the policy untestable without a model.
- **Catch-and-retry around the existing call in `AnalyseCmd` only.** Rejected: the classification of "retryable" would then be spread across both call sites.

The conversation is deliberately fresh each attempt, so nothing has to survive between attempts; this is what makes an external policy workable.

### Decision 2: A step outcome instead of a warning plus return value

`executeMessages()` returns a step outcome: either an accepted payload, or a failure carrying a reason, a bounded human-readable message, the schema violation messages, and the parser excerpt. Expected failures stop being exceptions and stop being warnings that the caller cannot see.

Alternatives considered:

- **Throw a typed exception for a rejected response.** Rejected: an expected outcome as control flow, and the retrier would have to catch and re-inspect it.
- **Keep the warning and validate again in the retrier.** Rejected: duplicated validation and a second place that decides what "conformant" means.

`executeMessages()` keeps `throws IOException` for genuine engine failures (chat log write, payload serialization). Those are fatal for the step and are not retried.

Reasons: `NO_RESPONSE`, `NO_PAYLOAD`, `PAYLOAD_NOT_PARSEABLE`, `SCHEMA_VIOLATION`, `RETRIABLE_MODEL_ERROR`. A fatal model error is not represented as a reason at all — it propagates as the exception it is, and the retrier classifies it as fatal.

### Decision 3: Failure classification

```
  retryable   NO_RESPONSE | NO_PAYLOAD | PAYLOAD_NOT_PARSEABLE
              | SCHEMA_VIOLATION | RETRIABLE_MODEL_ERROR
  fatal       NonRetriableException subtree, HttpException without a
              retriable marker, UnsupportedFeatureException,
              ToolExecutionException, IOException
```

The `RetriableException`/`NonRetriableException` split is used as-is rather than by inspecting HTTP status codes, so the engine follows the library's own classification.

### Decision 4: The repair hint is composed where the schema description is composed

`ChatExecutor` already rewrites the last `UserMessage`; the retrier passes an optional repair hint into the execution context, and the same code path appends it after the schema description. One place decides what a prompt contains, and the hint is visible in the chat log because it is part of the message.

Alternative considered: the retrier appends the hint to the message list itself. Rejected: two places writing into the same user message, and the ordering against the schema description would depend on call order.

The hint is bounded (violation count and characters). The bound is a constant, chosen in implementation.

### Decision 5: Publication and task status stay with `AnalyseCmd`

A step is published and marked successful only for an accepted outcome; an exhausted step is marked failed and writes nothing. `AnalyseCmd` keeps this logic where it already is, so `TaskManager` and the state file need no new API. For a loop task, `markIndexSuccessful` is still called per accepted index, which is what makes a successful index survive a failed sibling and be skipped on the next run.

### Decision 6: `retries` means attempts

`retries` is an `int` in `Config`, default 3, loaded from `config.json`, validated at load time (a value below 1 is a configuration error, not a clamp). It is printed by `dumpToLog()` and documented in the `README.md` configuration table. `ConfigOverrides` is untouched, since there is no CLI option.

### Decision 7: Attempt 1 keeps the existing log file name

`ChatLogger.writeLogFile()` gains the attempt number. Attempt 1 keeps `<taskId>[_<loopIndex>].log`, further attempts use `<taskId>[_<loopIndex>].attempt<N>.log`, and each attempt overwrites only its own file. This keeps the familiar name for the common case and makes every rejected attempt recoverable.

The rule is positional: attempt 1 is the plain name even when it is the attempt that failed. Making the plain name mean "the attempt that decided the step" would require moving the log write behind validation, which today runs after it.

### Decision 8: Retry reporting through the existing callback

`ProgressCallback` gains `onRetry(taskId, loopIndex, attempt, maxAttempts, reason)` with a no-op default, so `ProgressCallback.noOp()` and the three implementations keep compiling. `TaskRow` and `LoopWorkerRow` gain attempt fields for rendering; `SimpleOutput` prints one line per retry. The WARN record per retry travels the existing logging path, so it reaches the TUI's reserved line through `logLineSink` and the engine log file, with no new routing.

## Risks / Trade-offs

- **A failing step now costs up to three model calls.** → Bounded by the default budget, the prompt cost of an attempt is unchanged, and every retry is WARN-logged, so a run cannot silently triple its calls.
- **Temperature 0.0 makes a repeated attempt look deterministic.** → The repair hint changes the input between attempts, which is exactly why the hint carries the violation messages instead of a generic notice.
- **A permanently broken model setup would be retried.** → Authentication, unknown-model, invalid-request and unresolvable-server errors are classified fatal and spend no attempt; a run that keeps retrying is visible in the logs.
- **A retrying loop index occupies its pool slot longer.** → Attempts are sequential and bounded; the loop pool size is unchanged.
- **Behaviour change: a run that previously completed can now fail.** → This is intended: a non-conformant payload no longer counts as a success. Affected tasks reappear as failed in the TUI and are retried on the next run instead of publishing a wrong payload.
- **More files in `logs/`.** → One file per attempt, with a stable name pattern, so the directory stays predictable.
- **Hint text consumes prompt budget.** → The hint is bounded, and the violation list is the only variable part.

## Migration Plan

- The new configuration key has a default, so existing workspaces keep working without an edit.
- `analysis.json` needs no migration: payloads that a step fails to produce are simply not written, and a re-run overwrites the affected properties.
- Full rollback requires reverting the code, because the schema-violation change is not reachable through configuration. Setting `retries: 1` only restores the attempt count, not the previous accept-and-warn outcome.

## Open Questions

- The numeric bound on the hint (violation count and characters), and whether the parser excerpt length already used by `ResponsePayloadParser` is reused.
- Whether `state.json` should record the attempt count and the last failure reason, for `state dump`.
- Whether a `--retries` CLI option is wanted later. Adding it also changes `cli-config-precedence` and needs its own delta.
