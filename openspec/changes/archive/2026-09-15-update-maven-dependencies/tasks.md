## 1. POM Update

- [x] 1.1 Update all 15 dependency versions in `pom.xml` (LangChain4j trio 1.20.0, beta duo 1.20.0-beta30, Jackson 2.22.2, handlebars 4.5.5, json-schema-validator 3.0.7, fastcsv 4.4.0, cyclonedx-core-java 13.2.0, logback 1.6.3, jline pair 4.4.3) per `proposal.md` and verify `grep` shows exactly the target versions
- [x] 1.2 Update `junit.jupiter.version` property 6.1.1 -> 6.1.3 and verify junit-bom import resolves it
- [x] 1.3 Update 4 plugin versions (enforcer 3.6.3, compiler 3.16.0, jar-plugin 3.5.1, cyclonedx-maven-plugin 2.9.3) and verify no plugin left on old version

## 2. Build Verification

- [x] 2.1 Run `mvn verify` and verify build SUCCESS (compile + unit tests + SBOM generation)
- [x] 2.2 Run `mvn versions:display-dependency-updates` and verify no remaining updates (except deliberately pinned beta line)
- [x] 2.3 Inspect regenerated SBOM under `target/` and verify it lists new library versions (e.g. langchain4j 1.20.0, jline 4.4.3)

## 3. Runtime Smoke

- [x] 3.1 Smoke-test packaged fat jar `java -jar target/LLMAnalysisJinni-jar-with-dependencies.jar --help` and verify it starts (shade `--minimizeJar` did not strip needed classes)
- [x] 3.2 Run one short `analyse` + `document` in a scratch workspace with MCP tools and verify TUI/terminal output, MCP tool calls, and documentation render work with new jline/langchain4j versions
- [x] 3.3 Verify `analyse`/`document` outputs contain no new errors in logs and `state.json`/`analysis.json` written correctly

## 4. Wrap-up

- [x] 4.1 Commit pom update as single commit and mark change complete in OpenSpec (`openspec validate`, then archive per project workflow)
