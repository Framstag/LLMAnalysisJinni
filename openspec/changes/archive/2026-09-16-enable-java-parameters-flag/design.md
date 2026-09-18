## Context

Compile config in `pom.xml`: `maven-compiler-plugin` configures picocli annotation processing and passes `-Aproject`. No `<parameters>true</parameters>`. LangChain4j `ToolSpecifications` (1.20) reads parameter names reflectively; without `-parameters` javac flag they fall back to `arg0`. See `proposal.md` — Why.

## Goals / Non-Goals

**Goals:** All tool method parameter names available at runtime; `arg0` warnings gone.

**Non-Goals:** No `@P(name=...)` migration — `P` has no name attribute in langchain4j; `-parameters` is the supported fix. No changes to tool implementations.

## Decisions

**D1: `<parameters>true</parameters>` on existing `maven-compiler-plugin` config.**
Rationale: single source, applies to main + test compilation including picocli codegen path. `-parameters` is standard javac flag, no toolchain risk (Java 25 supports it; picocli codegen unaffected).
Alternative considered: kotlin-style per-plugin arg (irrelevant, all-Java project).

## Risks / Trade-offs

- [None significant] -> `MethodParameters` attribute adds trivial bytecode size.
- [Shade minimized jar] -> parameter names live in class files, `--minimizeJar` keeps them (names are not stripped by minimization).

## Migration Plan

1. Add `<parameters>true</parameters>` inside `maven-compiler-plugin` `<configuration>`.
2. Add unit test asserting `filesystem_get_all_files_in_dir` spec parameter name == `path`.
3. `mvn verify`; live: run one `analyse -o` task, confirm no `arg0` warnings in log.

## Open Questions

None.
