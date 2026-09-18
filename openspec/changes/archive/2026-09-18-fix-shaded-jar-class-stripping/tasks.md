# Tasks

## 1. Packaging configuration

- [x] 1.1 Remove `minimizeJar` from the `maven-shade-plugin` configuration in `pom.xml`; verify with `mvn verify -DskipTests` that `target/LLMAnalysisJinni-jar-with-dependencies.jar` is produced and that `unzip -l` lists `ch/qos/logback/classic/util/DefaultJoranConfigurator.class`, `ch/qos/logback/core/ConsoleAppender.class` and `com/ctc/wstx/stax/WstxInputFactory.class`
- [x] 1.2 Remove the two `org.jline:jline-terminal*` `include` filters, which only worked around minimization; verify the rebuilt jar still contains `org/jline/terminal/spi/TerminalProvider.class` and that the existing test suite still passes
- [x] 1.3 Record the artefact size change with `stat -c%s` before and after and confirm it stays within the ~30 MiB of dependency content stated in `design.md`; if it exceeds that, report the measured value in the change

## 2. Artefact verification in the build

- [x] 2.1 Add `maven-failsafe-plugin` to `pom.xml` bound to `integration-test` and `verify`, configured to run the artefact smoke test; verify `mvn verify` invokes it (failsafe summary in the build output)
- [x] 2.2 Add a test-scoped probe main class under `src/test/java/.../smoke/` that spawns `java -jar target/LLMAnalysisJinni-jar-with-dependencies.jar analyse <nonexistent-workspace>`, asserts the process exits non-zero and prints at least one non-empty line carrying a log level and logger name, and asserts `XMLInputFactory.newInstance()` and `XMLOutputFactory.newInstance()` succeed from the packaged classpath; verify the smoke test passes against the fixed jar and needs no LLM endpoint or network
- [x] 2.3 Extend the probe to enumerate every `META-INF/services/<interface>` entry in the packaged jar and fail when a named implementation class cannot be loaded from that jar; verify the check runs in `mvn verify` and reports the count of providers checked
- [x] 2.4 Confirm the guard actually catches the defect: temporarily restore `minimizeJar=true`, run `mvn verify`, and verify the build fails with a message naming the packaged artefact and the missing capability; then revert the temporary change and verify the build passes again

## 3. Documentation and end-to-end confirmation

- [x] 3.1 Update `AGENTS.md` (and `README.md` wherever the uber-jar run path is described) to state that the uber-jar is covered by the `verify` phase and that a jar run producing no output at all is a packaging defect rather than a silent success; verify both files describe the same contract and that `mvn exec:java` remains the primary documented workflow
- [x] 3.2 Rebuild the artefact and run the originally reported command `java -jar target/LLMAnalysisJinni-jar-with-dependencies.jar analyse --execution-trace=true workspaces/spring-petclinic`; verify console output appears (at minimum the display-mode line, config/effective-setting log lines and task start lines), capturing the first output lines as evidence
- [x] 3.3 Run the full `mvn verify` on the final state and record the result, including the new smoke test and the pre-existing test suite
- [x] 3.4 Update `guidelines/TestApproach.md`, which stated that the project uses unit tests solely, so that it documents the artefact smoke test and its constraints (offline, fast, no model endpoint); verify the guideline no longer contradicts the build and that its unit-test style rules are unchanged

## 4. Verification follow-ups

- [x] 4.1 Correct the spec so the SBOM scenario no longer claims XML-based parsing: split it into an XML factory requirement and an SBOM parsing requirement that matches the SBOM tool's parser path (`SBOMTool.loadSBOM`); verify `openspec validate --strict` still passes
- [x] 4.2 Cover the execution-trace and progress scenarios with a test: run the artefact with `--execution-trace=true --single-step=true` against a generated workspace whose model endpoint is a closed local port, and assert the display-mode line and a task start line; verify the test passes offline and finishes in seconds
- [x] 4.3 Cover the SBOM scenario with the probe: parse `target/bom.json` through `SBOMTool` from the packaged classpath and assert an `OK` result and a non-zero dependency count; verify the check runs in `mvn verify`
- [x] 4.4 Update `design.md` so it matches the implementation: document the `SERVICE_PROVIDER_MISSING` versus `SERVICE_PROVIDER_UNRESOLVED` split in D3, the SBOM and trace cases in D2, and the risk that the guard's own detection is no longer triggered by the build
- [x] 4.5 Cover the guard itself and align the test class with the existing test style: strip a service provider class from a copy of the artefact and assert the probe fails naming the missing capability, and declare `JarSmokeIT` public; verify `mvn verify` runs 4 artefact tests successfully

## 5. Build ordering fix found during verification

- [x] 5.1 Fix the pre-existing failure of `mvn clean package`: the SBOM tests parse `target/bom.json`, which the cyclonedx plugin generated in the `package` phase, after the `test` phase, so three `SBOMToolTest` tests failed on a clean build; verified the failure also occurs on an unmodified checkout. Move the `makeAggregateBom` execution to `process-classes` and verify `mvn clean package` succeeds with all 112 unit tests
- [x] 5.2 Confirm the generated SBOM is unchanged by the earlier phase: compare `target/bom.json` before and after the change (identical except the timestamp) and confirm the 57 components and 58 dependency entries are preserved
- [x] 5.3 Update the build instructions in `AGENTS.md` and `README.md`, which described the two-step build that the ordering fix makes unnecessary, and verify the documented commands match the verified behaviour
- [x] 5.4 Run `mvn clean verify` from scratch on the final state and confirm the SBOM generation, the unit tests, the unit-test-to-integration-test ordering and the four artefact tests all succeed in one run
