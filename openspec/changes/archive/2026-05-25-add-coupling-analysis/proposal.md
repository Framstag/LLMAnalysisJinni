## Why

The project currently captures method complexity, inheritance, visibility, nesting depth, and field patterns — but zero measure of how classes depend on each other. Coupling (fan-out) is one of the most fundamental quality metrics in software architecture: highly-coupled classes are fragile, hard to test, and violate Single Responsibility. Adding per-class efferent coupling (Ce) and cross-module dependency analysis lets the LLM identify hub classes, fragile modules, and dependency direction violations — completing the quality coverage alongside cohesion (field analysis).

## What Changes

- Add `efferentCoupling` (int) to `Clazz.java` — count of unique external types referenced (imports minus same-package types)
- Compute coupling during report generation in `JavaTool.java` — for each class, match its imports against its own package/module; count external types
- Add 1 new `@Tool` method: `java_get_coupling_report` returning coupling distribution + module dependency graph
- Add 1 new analysis task in `tasks.yaml` for LLM evaluation of coupling patterns (hub classes, unstable modules)
- No new dependencies — reuses existing `imports` on `BuildUnit` and fully-qualified type names

## Capabilities

### New Capabilities

- `java-coupling-analysis`: Evaluates per-class efferent coupling distribution and inter-module dependency patterns. Enables LLM to detect hub classes (high fan-out), tightly-coupled modules, and dependency stability issues.

### Modified Capabilities

- *(None — new standalone capability)*

## Impact

- **Data model**: `Clazz.java` — add 1 int field (`efferentCoupling`), computed at report time
- **Tools**: `JavaTool.java` — add 1 new `@Tool` method returning coupling distribution across classes (separate prod/test/gen) plus module-level dependency counts
- **Pipeline**: `tasks.yaml` — add 1 new task looping over modules
- **No new dependencies** — reuses existing `imports` on `BuildUnit` and fully-qualified class names