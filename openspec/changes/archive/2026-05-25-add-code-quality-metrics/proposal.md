## Why

The current analysis covers complexity (CC, nesting), structure (inheritance, coupling, fields), and visibility — but misses four lightweight, high-signal metrics that require minimal or no new data model changes:

- **Method count per class**: quick god-class detection without complex cohesion math
- **Documentation ratio**: javadoc/comment coverage per module — data already exists on `Method` and `Clazz`
- **Data class detection**: combines existing field visibility + method signature data to identify anemic domain models precisely
- **Boolean parameter flags**: methods with 3+ boolean parameters are a classic "flag argument" smell — trivial to detect from existing method descriptor data

All four require ~50 lines of Java total and slot into the existing pipeline pattern.

## What Changes

- Add `MethodCountEvaluation` task — distributions of method count per class
- Add `DocumentationRatioEvaluation` task — % documented classes/methods per module
- Add `DataClassDetectionEvaluation` task — identifies classes matching data class criteria (all fields private OR public, only getters/setters, no business logic)
- Add `BooleanParameterEvaluation` task — methods with 3+ boolean parameters
- 4 new `@Tool` methods in `JavaTool.java`
- 4 new prompts, 4 new tasks in `tasks.yaml`
- 4 new documentation sections in `Documentation.md.hbs`
- No new dependencies

## Capabilities

### New Capabilities

- `java-method-count-analysis`: Distribution of method count per class per module. Enables LLM to detect god classes.
- `java-documentation-ratio-analysis`: Ratio of documented (javadoc/comment) classes and methods per module. Enables LLM to flag undocumented code.
- `java-data-class-detection`: Identifies classes matching data class criteria (all fields private + only getters/setters, or all fields public) per module. Enables LLM to detect anemic domain models precisely.
- `java-boolean-parameter-analysis`: Methods with 3+ boolean parameters per module. Enables LLM to detect flag argument smells.

### Modified Capabilities

- *(None — all new)*

## Impact

- **Tools**: `JavaTool.java` — add 4 new `@Tool` methods
- **Pipeline**: `tasks.yaml` — add 4 new tasks
- **Documentation**: `Documentation.md.hbs` — add 4 new sections
- **No data model changes** — all metrics computed from existing fields