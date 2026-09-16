## Why

LangChain4j 1.20 (arrived with the 2026-09 dependency update) warns at tool registration: `Parameter 'arg0' of tool method ... has no name available at runtime`. Without javac's `-parameters` flag, reflection exposes parameter names as `arg0`, `arg1`, ... even though `@P(description)` supplies the description. Meaningless names degrade LLM tool-calling accuracy.

## What Changes

- `pom.xml`: add `<parameters>true</parameters>` to the `maven-compiler-plugin` configuration so javac embeds `MethodParameters` attributes.
- Add unit test: build a `ToolService` from `FilesystemTool`, find `filesystem_get_all_files_in_dir`, assert its single parameter is named `path` (not `arg0`).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. Build configuration only; no analysis capability or engine behavior changes. Explicit opt-out via `skip_specs: true`.

## Impact

- `pom.xml` (compiler plugin block); bytecode grows slightly (parameter-name attributes).
- Runtime: `ToolSpecification` parameter names become real names → `arg0` warnings disappear, tool-call accuracy improves.
- Verification: unit test + `mvn verify` + one live `analyse` task run checking the warnings are gone.
