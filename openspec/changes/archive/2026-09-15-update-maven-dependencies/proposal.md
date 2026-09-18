## Why

Dependencies drifted behind latest releases. LangChain4j ecosystem advanced 1.17.1 -> 1.20.0 (stable line) / 1.20.0-beta30 (beta line), several libraries have patch/minor/major releases available. Keeping dependencies current is required to receive bug fixes and stay compatible with upstream APIs. Current versions are stale by months; several `pom.xml` entries already carry `<!-- Use latest -->` markers.

## What Changes

Update Maven dependency versions in `pom.xml` (scenario A: full update, no intermediate pinning):

**Dependencies**
- `dev.langchain4j:langchain4j` 1.17.1 -> 1.20.0
- `dev.langchain4j:langchain4j-ollama` 1.17.1 -> 1.20.0
- `dev.langchain4j:langchain4j-open-ai` 1.17.1 -> 1.20.0
- `dev.langchain4j:langchain4j-local-ai` 1.17.1-beta27 -> 1.20.0-beta30 (beta line, no stable release exists; beta already in use)
- `dev.langchain4j:langchain4j-mcp` 1.17.1-beta27 -> 1.20.0-beta30 (beta line, no stable release exists; beta already in use)
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` 2.22.0 -> 2.22.2
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` 2.22.0 -> 2.22.2
- `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` 2.22.0 -> 2.22.2
- `com.github.jknack:handlebars` 4.5.3 -> 4.5.5
- `com.networknt:json-schema-validator` 3.0.5 -> 3.0.7
- `de.siegmar:fastcsv` 4.3.1 -> 4.4.0
- `org.cyclonedx:cyclonedx-core-java` 12.2.0 -> 13.2.0 (major)
- `ch.qos.logback:logback-classic` 1.5.37 -> 1.6.3
- `org.jline:jline-terminal` 3.29.0 -> 4.4.3 (major)
- `org.jline:jline-terminal-ffm` 3.29.0 -> 4.4.3 (major)
- `junit.jupiter.version` property 6.1.1 -> 6.1.3 (junit-bom import)

**Plugins**
- `maven-enforcer-plugin` 3.6.2 -> 3.6.3
- `maven-compiler-plugin` 3.15.0 -> 3.16.0
- `maven-jar-plugin` 3.5.0 -> 3.5.1
- `org.cyclonedx:cyclonedx-maven-plugin` 2.9.1 -> 2.9.3

**Explicitly NOT updated**
- No betas elsewhere (rule: beta only where beta already chosen).
- Plugin 4.0.0-beta-N releases skipped (require Maven 4 + beta).
- `maven-shade-plugin` 3.6.2, `maven-site-plugin` 4.0.0-M16, `javaparser-symbol-solver-core` 3.28.2, `picocli` 4.7.7, `picocli-codegen` 4.7.7 = already latest.

## Capabilities

### New Capabilities

None. Dependency update changes no analysis capability or engine behavior.

### Modified Capabilities

None. No requirements change; specs describe analysis behavior, which is untouched. Explicit opt-out via `skip_specs: true` in `.openspec.yaml` (dependency/tooling change only).

## Impact

- **Code**: none. `pom.xml` version numbers only (dependencies + plugins + property).
- **Build**: compile must pass; jar contents of packaged fat jar change (updated libs).
- **Risk hotspots** (verified API surface present in target jars, but need compile + runtime confirmation):
  - `org.jline` 3 -> 4 major: `TerminalSupport.java` uses `Terminal`, `TerminalBuilder`, `DumbTerminal`, `InfoCmp.Capability` — all present in 4.4.3; TUI rendering needs a live run check.
  - `org.cyclonedx:cyclonedx-core-java` 12 -> 13 major: `SBOMTool.java` uses `parsers.JsonParser` + `model.*` — present in 13.2.0; SBOM tests cover parsing.
  - langchain4j 3 minor jumps on stable + beta line: `ToolServiceFactory.java`, `ChatExecutor.java` use `mcp.client` / `service.tool` APIs; compile + tests + MCP runtime spot-check required. All 5 langchain4j modules must move in lockstep (mixing stable 1.20.0 with 1.17.1-beta27 MCP would break API alignment).
- **SBOM**: generated SBOM reflects new library versions (first `mvn verify` regenerates it — build is not dependency-reduced).
