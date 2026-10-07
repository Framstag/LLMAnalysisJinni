# Design

## Context

See `proposal.md` for motivation. The constraints that shape the approach:

- `pom.xml` pins `langchain4j`, `langchain4j-ollama`, `langchain4j-open-ai` at 1.20.0 and `langchain4j-local-ai`, `langchain4j-mcp` at 1.20.0-beta30. The project's rule is beta only where beta is already in use.
- The engine's langchain4j surface: `ChatModelFactory` (three model builders), `ChatExecutor` (tool loop, handlers, tool execution, JSON schema requests), `ToolServiceFactory` (local `@Tool` objects plus MCP client tools).
- `add-tool-error-feedback` needs `ToolArgumentsErrorHandler.sendExceptionMessageToLlm()` and `ToolErrors`, which exist only from 1.21.0. In 1.20.0 the interface has no static factories at all.
- 1.21.0 does not fix issue #14: `DefaultToolExecutor.coerceArgument` still deserializes collection/array parameters through `Json.fromJson(Json.toJson(argument), parameterType)`, and `JacksonJsonCodec` still does not enable `ACCEPT_SINGLE_VALUE_AS_ARRAY`.
- Build constraints: the SBOM is generated in `process-classes`, `mvn verify` runs the packaged-jar smoke test, `minimizeJar` must stay disabled, and `ServicesResourceTransformer` is required in the shade configuration.
- Precedent: archived change `2026-09-15-update-maven-dependencies` moved the same five modules 1.17.1 -> 1.20.0 with `skip_specs: true` and no spec delta.

## Goals / Non-Goals

**Goals:**

- Land the 1.21.0 line for all five langchain4j modules so the follow-up change can use upstream handlers instead of hand-written ones.
- Keep engine behaviour identical: this change is a version bump plus whatever the compiler demands.
- Leave the verification trail that shows the two things the upgrade does *not* change: the coercion defect and the sync default of the arguments error handler.

**Non-Goals:**

- No engine behaviour change. If behaviour changes, that is a finding, not part of this change.
- No other dependency or plugin version in the same step.
- No Jackson 3 adoption.
- No spec delta: dependency versions are not behaviour.

## Decisions

### Decision 1: Move all five modules to the 1.21.0 line, nothing else

`langchain4j`, `-ollama`, `-open-ai` -> 1.21.0; `-local-ai`, `-mcp` -> 1.21.0-beta31.

Alternatives considered:

- **1.20.2 (patch step).** Rejected: it does not carry the handler API, so the follow-up change would hand-write what upstream ships.
- **Bump only `langchain4j`.** Rejected: mixing a 1.21.0 core with 1.20.0 model modules and a 1.20.0-beta30 MCP client breaks API alignment between modules that share `service.tool` types.
- **Stay on 1.20.0.** Rejected: it blocks the follow-up change and leaves the project behind a released line.

### Decision 2: No unrelated version bumps

The archived dependency change already moved every other library. Isolating this step means a red build has one candidate cause.

### Decision 3: No spec delta, `skip_specs: true`

Dependency versions are not externally visible behaviour. The precedent change did the same, and `openspec validate` accepts the change with that marker.

### Decision 4: Jackson stays on 2.x

1.21.0 supports Jackson 3 as an add-on dependency. Rejected here: the codec sits exactly on the tool-argument path that issue #14 is about, and the upgrade already changes the codec's visibility configuration. Two moving parts on the same path would make the follow-up change's verification ambiguous.

### Decision 5: Verification gate in fixed order

1. `mvn verify -DskipTests` — compile against the new API.
2. `mvn test` — unit tests, including `ToolServiceFactoryTest` and the tool tests.
3. `mvn verify` — SBOM regeneration plus `smoke/JarSmokeIT`.
4. A live `analyse` run against a real backend, exercising at least one tool call, which is where a codec visibility change would surface.

Result so far: steps 1 to 3 pass (223 unit tests, 6 packaged-jar smoke tests, SBOM tests included). Step 4 could not be run in this environment, because no model backend was reachable; see the open question at the end of this document.

Upstream deltas that this gate is meant to catch or confirm are listed in `proposal.md` under "Upstream behaviour deltas".

## Verification findings

Checked against the artefact this change builds (`target/LLMAnalysisJinni-jar-with-dependencies.jar`, inspected with `javap`), not against documentation or release notes, so `add-tool-error-feedback` reads them as evidence:

1. **The coercion defect is unchanged.** `DefaultToolExecutor.coerceArgument` still carries the `Collection`/`Map` branch that routes the value through `Json.toJson` and `Json.fromJson`; there is no scalar-to-singleton wrapping. Issue #14 is therefore not fixed by this upgrade, and the follow-up change is required.
2. **`JacksonJsonCodec` does not enable `ACCEPT_SINGLE_VALUE_AS_ARRAY`.** It enables `FAIL_ON_UNKNOWN_PROPERTIES` and `ACCEPT_CASE_INSENSITIVE_ENUMS`, disables `INDENT_OUTPUT`, and adds `visibility(FIELD, ANY)`. Only the visibility setting is new relative to 1.20.0.
3. **The handler API the follow-up change needs exists.** `ToolArgumentsErrorHandler` has `failInvocation()` and `sendExceptionMessageToLlm()`, and `ToolService` defaults are: arguments handler fails the invocation (throws), execution handler sends the message to the model; the async defaults are the other way round (arguments to the model, execution fails unless the exception is visible to the model).
4. **`ToolService.maxToolCallingRoundTrips` still defaults to 100.**
5. **A new transitive beta dependency ships in the fat jar**: `langchain4j-reactive-streaming` 1.21.0-beta31 arrives through `langchain4j-open-ai` 1.21.0 (8 `dev/langchain4j/reactive` classes are in the packaged jar). It is not part of the direct dependency set that `SBOMToolTest` pins, so that expectation did not change.
6. **No engine code change was required.** The 1.21.0 API is source compatible with every call the engine makes. Only two hard-coded test expectations needed updating: the pinned langchain4j versions in `tools/sbom/SBOMToolTest.java`, and the source-tree listing in `tools/filesystem/FileToolTest.java` that enumerates the test files.

## Risks / Trade-offs

- **API drift 1.20.0 -> 1.21.0 in the classes the engine calls** (`ToolService`, `ToolExecutor`, `InvocationContext`, chat request/response types, JSON schema types, model builders) -> the compile gate names every site; expected fallout is confined to `ChatExecutor`, `ChatModelFactory` and `ToolServiceFactory`.
- **`JacksonJsonCodec` now sets `visibility(FIELD, ANY)`** and synthesizes polymorphic metadata for sealed types, on the tool-argument path -> covered by the tool tests plus step 4 of the gate. If it breaks argument handling, the documented `JsonCodecFactory` SPI is the escape hatch, but the SPI is marked internal upstream and is a last resort.
- **`JacksonJsonCodec` is annotated `@Internal`** -> the engine must not depend on it; it does not today, and neither this change nor the follow-up introduces such a dependency.
- **Shaded jar drops runtime-resolved classes** -> `minimizeJar` stays disabled and `ServicesResourceTransformer` stays configured; `smoke/JarSmokeIT` fails the build when the packaged jar goes quiet.
- **SBOM tests parse `target/bom.json`** -> run the full `mvn verify`, and do not move SBOM generation behind the test phase.
- **Beta modules** (`local-ai`, `mcp`) carry beta risk -> `local-ai` is not the default provider, and MCP is only active when configured; `ToolServiceFactoryTest` covers the wiring.
- **Silent behaviour change** in the tool error paths that the follow-up change depends on -> the follow-up change's tests pin the argument and execution handler behaviour explicitly, so a difference would fail there rather than in production.

## Migration Plan

1. Bump the five versions in `pom.xml`.
2. Run the verification gate above; fix only what the compiler or tests require.
3. Run one live `analyse` against a real project and backend.
4. Archive the change, then start `add-tool-error-feedback`.

Rollback: revert the `pom.xml` bump; no persisted state, configuration or analysis output depends on the version.

## Open Questions

- **A live run against a real backend is still missing** (task 3.2 and the run part of 3.3). No model backend answered on the default Ollama port or on the common OpenAI-compatible ports, so the one check that would exercise the changed tool-argument deserialization path against a real model is unverified. Options: bring a backend up and repeat the run, or accept the unit-test coverage and record the gap.
- Whether the beta modules should be pinned back to a stable line once one exists. Not part of this change; the project's rule keeps beta where beta already is.
- Whether the other libraries the archived change left untouched have releases that matter for this upgrade. Out of scope: one candidate cause per build.
- The newly shipped transitive beta (`langchain4j-reactive-streaming` 1.21.0-beta31) sits in the artifact without being a declared dependency. Whether to declare it explicitly, so the version is visible in `pom.xml`, is a follow-up decision.
