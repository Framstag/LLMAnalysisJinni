## Why

The project already distinguishes production vs test code via `BuildUnit.isProduction()`, but has no tool to map production classes to their corresponding test classes. Without this, the LLM cannot evaluate which classes lack tests, what the test-to-production ratio is per module, or whether core domain classes are tested. Adding a test coverage mapping tool closes this gap with minimal code — using naming convention matching on existing class data.

## What Changes

- Add 1 new `@Tool` method: `java_get_test_coverage_report` returning per-class test coverage status and module-level statistics
- Add 1 new analysis task in `tasks.yaml` for LLM evaluation of test coverage gaps
- No data model changes — reuses existing `isProduction()` on `BuildUnit` and class names

## Capabilities

### New Capabilities

- `java-test-coverage-analysis`: Evaluates test coverage per module using naming convention matching (production class `Foo` → test class `FooTest`, `FooImpl` → `FooImplTest`). Enables LLM to detect untested classes, compute test-to-production ratio, and identify gaps in core domain coverage.

### Modified Capabilities

- *(None — new standalone capability)*

## Impact

- **Tools**: `JavaTool.java` — add 1 new `@Tool` method returning per-class coverage status and module-level statistics
- **Pipeline**: `tasks.yaml` — add 1 new task looping over modules
- **No data model changes** — reuses existing `BuildUnit.isProduction()` and class qualified names