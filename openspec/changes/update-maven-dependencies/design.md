## Context

`pom.xml` pins every dependency explicitly (no BOM for LangChain4j/Jackson, one property for JUnit). Current state before change: LangChain4j 1.17.1 stable + 1.17.1-beta27 beta line, JLine 3.29.0, CycloneDX 12.2.0. Motivation: see `proposal.md` — Why.

Verified against published artifacts (jar inventory, not guesses):
- JLine 4.4.3 keeps `org.jline.terminal.Terminal`, `TerminalBuilder`, `impl.DumbTerminal`, `org.jline.utils.InfoCmp$Capability` — all classes `TerminalSupport.java` imports exist.
- CycloneDX 13.2.0 keeps `org.cyclonedx.parsers.JsonParser` / `BomParserFactory` and `model.*` used by `SBOMTool.java`.

## Goals / Non-Goals

**Goals:**
- Bump all updateable dependencies and plugins to newest stable (or established beta line) version at once, keeping LangChain4j modules in lockstep.
- Pass `mvn verify` after the update (compile + tests + SBOM generation).

**Non-Goals:**
- No dependency-reduction, no adding new dependencies, no code changes, no BOM restructuring.
- No Maven 4 migration (plugin 4.0.0-beta-N releases stay out — they require Maven 4 and are betas).
- No stable-ification of `langchain4j-local-ai` / `langchain4j-mcp`: no stable release exists upstream; beta line follows.

## Decisions

**D1: Scenario A — jump LangChain4j to latest (1.20.0 / 1.20.0-beta30) instead of incremental pinning.**
Rationale: single leap to current releases matches the `<!-- Use latest -->` intent markers in `pom.xml`; avoids two intermediate update cycles. The 1.17.1 line is 3 minors old.
Alternatives considered: (B) pin to patch 1.17.2/1.17.2-beta27 — zero API drift but keeps stale line; (C) 1.19.3/1.19.3-beta29 — middle ground. Rejected: A chosen; B/C remain fallback if A fails compile.

**D2: Lockstep across all 5 LangChain4j modules.**
`langchain4j-local-ai`/`-mcp` versions track core (`1.20.0-beta30` = beta of the 1.20 line). Mixing 1.20.0 core with 1.17.1-beta27 MCP would pair mismatched API generations. All five update in one commit.

**D3: Keep beta rule — no new betas.**
`-beta30` only for local-ai/mcp where beta already chosen and no stable exists; every other artifact updates to stable. Plugin betas skipped.

**D4: Explicit version numbers, same style as today.**
No new `<dependencyManagement>` entries, no properties beyond the existing `junit.jupiter.version`. Minimizes pom diff, keeps SBOM non-reduced output unchanged in structure.

## Risks / Trade-offs

- [LangChain4j API drift across 3 minors in `mcp.client` / `service.tool` (used by `ToolServiceFactory.java`, `ChatExecutor.java`)] → compile + existing tests in `mvn verify`; manual MCP spot-run in a workspace; fallback to scenario B (1.17.2/1.17.2-beta27) if blocked.
- [JLine 3->4 major: API surface verified but terminal behavior (ANSI/unicode detection, shutdown) may shift] → `mvn verify` compile gate; live `analyse` run shows TUI output; `TerminalSupport.detect()` already degrades to `none()` on failure.
- [CycloneDX 12->13 major: model/parser changes beyond class existence] → SBOM tool tests inside `mvn verify`; SBOM regenerates on first build (tests depend on generated artifacts).
- [FastCSV 4.3.1->4.4.0 minor API tweaks] → compile; CSV tool tests cover writing.
- [`--minimizeJar` shade config may strip classes if new versions relocate/reflective-load differently] → JLine include filters already added keep terminal classes; verify packaged jar starts (`java -jar` smoke).

## Migration Plan

1. Edit `pom.xml` versions (one commit, atomic).
2. `mvn verify` — compile + tests + SBOM regeneration.
3. Smoke: `java -jar target/LLMAnalysisJinni-jar-with-dependencies.jar --version` (or `help`), plus one short `analyse`/`document` run in a scratch workspace to exercise MCP + TUI paths.
4. Inspect regenerated `target/sbom*` to confirm new versions appear.
5. Rollback = revert the single commit; pom is the only touched file (plus regenerated SBOM artifacts under `target/`).

## Open Questions

None blocking. If `mvn verify` fails on LangChain4j API use, scope decision: repair code (out of current scope — would open code changes) vs. fall back to scenario B; user decides at that point.
