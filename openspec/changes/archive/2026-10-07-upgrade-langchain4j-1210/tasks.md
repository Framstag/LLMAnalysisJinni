# Tasks

## 1. Dependency bump

- [x] 1.1 Bump the five langchain4j modules in `pom.xml` (`langchain4j`, `langchain4j-ollama`, `langchain4j-open-ai` to 1.21.0; `langchain4j-local-ai`, `langchain4j-mcp` to 1.21.0-beta31) and verify with `mvn -q -DskipTests dependency:resolve` that every version resolves
- [x] 1.2 Verify no other dependency or plugin version moved in the same step: `git diff pom.xml` shows the five version changes only
- [x] 1.3 Record the confirmed upstream deltas (coercion path unchanged, sync handler defaults unchanged, `maxToolCallingRoundTrips` default 100) in this change's `design.md` as checked findings, so the follow-up change reads them as evidence rather than assumptions

## 2. Compile and unit tests

- [x] 2.1 Run `mvn verify -DskipTests` and fix only what the compiler requires in `lc4j/ChatExecutor.java`, `lc4j/ChatModelFactory.java` and `tools/ToolServiceFactory.java`, checking each fix against the 1.21.0 API rather than guessing
- [x] 2.2 Run `mvn test` and confirm the suite passes, in particular `tools/ToolServiceFactoryTest.java` and the `lc4j` tests
- [x] 2.3 Add a unit test that wires `ToolArgumentsErrorHandler.sendExceptionMessageToLlm()` into a `ToolService` and asserts the configured handler returns a text result instead of throwing, so the API the follow-up change depends on is pinned by a test rather than by a compile-time accident

## 3. Integration verification

- [x] 3.1 Run `mvn verify` and confirm the SBOM tests and `smoke/JarSmokeIT` pass against the packaged jar, with `minimizeJar` still disabled
- [x] 3.2 Run a live `analyse` against an existing workspace and a real backend, confirm at least one tool call executes and the run reaches successful tasks, and confirm `logs/engine.log` carries no new warnings compared to a run on the previous version
- [x] 3.3 Verify `state.json` and `analysis.json` written by that run load and dump as before (`state dump`) and that no run output changed shape
- [x] 3.4 Run `openspec validate upgrade-langchain4j-1210 --strict` and confirm the change validates with `skip_specs: true`
