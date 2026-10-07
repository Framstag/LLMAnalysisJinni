# Proposal

## Why

The engine pins langchain4j at 1.20.0 (1.20.0-beta30 for `local-ai` and `mcp`), the 1.21.0 line is released, and the follow-up change `add-tool-error-feedback` needs API that exists only from 1.21.0: `ToolArgumentsErrorHandler.sendExceptionMessageToLlm()` / `failInvocation()`, `ToolErrors`, `ToolErrorVisibleToLlm` and `ToolService.hasExplicitArgumentsErrorHandler()`. Without this upgrade that change has to hand-write handlers that upstream now ships.

This upgrade does **not** fix the defect of issue #14. Verified against the 1.21.0 sources: `DefaultToolExecutor.coerceArgument` still routes collection/array parameters through `Json.fromJson(Json.toJson(argument), parameterType)`, and `JacksonJsonCodec` still does not enable `ACCEPT_SINGLE_VALUE_AS_ARRAY`. A scalar emitted for an array-typed parameter still fails to coerce in 1.21.0.

## What Changes

Upgrade the langchain4j modules only, all five in lockstep with the version line the project already follows:

- `dev.langchain4j:langchain4j` 1.20.0 -> 1.21.0
- `dev.langchain4j:langchain4j-ollama` 1.20.0 -> 1.21.0
- `dev.langchain4j:langchain4j-open-ai` 1.20.0 -> 1.21.0
- `dev.langchain4j:langchain4j-local-ai` 1.20.0-beta30 -> 1.21.0-beta31 (beta line, no stable release exists; beta already in use)
- `dev.langchain4j:langchain4j-mcp` 1.20.0-beta30 -> 1.21.0-beta31 (beta line, no stable release exists; beta already in use)

No other dependency is moved in this change. The archived change `2026-09-15-update-maven-dependencies` already moved everything else; mixing an engine API upgrade with unrelated version bumps would make a failing build ambiguous.

No engine code change is intended. The gate is `mvn verify` staying green, including the SBOM tests and `smoke/JarSmokeIT`.

Upstream behaviour deltas that touch paths the engine uses, recorded so they are checked rather than assumed:

- The sync default `ToolArgumentsErrorHandler` still fails the invocation (`failInvocation()`); the async defaults are the ones that hand the message to the model. The engine configures its handler explicitly, so this default does not decide the engine's behaviour.
- The default `ToolExecutionErrorHandler` still returns the error text to the model.
- `ToolService.maxToolCallingRoundTrips` still defaults to 100.
- `JacksonJsonCodec` now sets `visibility(FIELD, ANY)` and synthesizes polymorphic metadata for sealed types, and is annotated `@Internal`. It still enables `FAIL_ON_UNKNOWN_PROPERTIES`, still enables case-insensitive enums, still disables indent output, and still does not enable `ACCEPT_SINGLE_VALUE_AS_ARRAY`. The visibility change affects how lc4j deserializes tool arguments, so it needs a covered test rather than a code review.
- Jackson stays on 2.x. Adopting Jackson 3 is a separate decision.

## Capabilities

### New Capabilities

None. A dependency upgrade changes no analysis capability and no engine behaviour.

### Modified Capabilities

None. No requirement changes. Explicit opt-out via `skip_specs: true` in `.openspec.yaml`, following the precedent of the archived `2026-09-15-update-maven-dependencies` change.

## Impact

- **Code**: `pom.xml` version numbers only, unless the new API line forces a compile fix.
- **Build**: compile gate `mvn verify -DskipTests`; full `mvn verify` regenerates `target/bom.json` and runs the packaged-jar smoke test. The shaded jar contents change. `ServicesResourceTransformer` must stay configured, because the 1.21.0 tool-error handling is reached through service-loaded and handler-injected classes, not through compiled call sites alone.
- **Risk — API drift 1.20.0 -> 1.21.0** in the classes this engine calls: `ToolService`, `ToolExecutor`, `DefaultToolExecutor`, `ToolArgumentsErrorHandler`, `ToolExecutionErrorHandler`, `HallucinatedToolNameStrategy`, `InvocationContext`, `ChatRequest`/`ChatResponse`/`ChatRequestParameters`, `JsonSchema`/`JsonRawSchema`, `MessageWindowChatMemory`, and the three model builders.
- **Risk — tool argument deserialization**: the `JacksonJsonCodec` visibility change sits on the tool-argument path. Covered by the existing tool tests plus a live tool call against a real backend before the change is archived.
- **Not affected**: `analysis/software-architecture` (tasks, prompts, response schemas), DAG scheduling and tag resolution, `state.json`, `analysis.json`, `config.json` format.

## Open Questions

- Fallback if 1.21.0 does not compile or breaks tests: 1.20.2 is the small patch step, but it does not carry the handler API, so using it means the follow-up change hand-writes what upstream ships.
- Whether the beta modules should stay on the beta line at all, or whether `local-ai`/`mcp` should be pinned back until a stable release exists. The project's established rule is beta only where beta is already in use, which this change follows.
- Whether the Jackson 3 adoption upstream now supports should be part of this upgrade. Not in this change; it is an independent decision with its own risk.
