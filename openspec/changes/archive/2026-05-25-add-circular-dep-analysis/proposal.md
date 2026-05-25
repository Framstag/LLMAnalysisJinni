## Why

The project can now analyze per-class coupling (fan-out), class cohesion, field visibility, inheritance, and test coverage — but none of these detect **circular dependencies** between classes or packages. Circular deps (A→B→C→A) are one of the most critical architecture smells: they make code impossible to test in isolation, prevent modular compilation, often indicate misplaced responsibilities, and degrade over time if not surfaced. The import data already available per `BuildUnit` is sufficient to build a dependency graph and detect cycles.

## What Changes

- Add 1 new `@Tool` method: `java_get_circular_dependency_report` that builds a dependency graph from `BuildUnit` imports and detects strongly connected components (SCCs) containing cycles
- Report lists each cycle found with the classes/packages involved
- Add 1 new analysis task in `tasks.yaml` for LLM evaluation of detected cycles
- No data model changes — reuses existing `imports` on `BuildUnit` and fully-qualified class/package names

## Capabilities

### New Capabilities

- `java-circular-dependency-analysis`: Detects circular dependencies between classes within a module using Tarjan's SCC algorithm on the import-based dependency graph. Enables LLM to identify and evaluate architecture cycles.

### Modified Capabilities

- *(None — new standalone capability)*

## Impact

- **Tools**: `JavaTool.java` — add 1 new `@Tool` method returning list of detected cycles
- **Pipeline**: `tasks.yaml` — add 1 new task
- **No data model changes** — reuses existing `imports` and class qualified names