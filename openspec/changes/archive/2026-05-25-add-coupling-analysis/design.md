## Context

The project already captures per-class metadata (methods, fields, inheritance, visibility) but lacks any measure of coupling — how many external types each class depends on. The `imports` list already exists on `BuildUnit` (file-level), and fully-qualified class names are available. Coupling can be computed at report time by matching imports against known module/package types.

This design follows the established pattern: compute distribution in @Tool → LLM evaluates.

## Goals / Non-Goals

**Goals:**
- Add `efferentCoupling` (int) to `Clazz.java`
- Compute coupling at report time in `JavaTool.java` — for each class, count imports that resolve to types outside the class's own package
- Add 1 `@Tool`: `java_get_coupling_report` returning coupling distribution per module (prod/test/gen) plus module dependency counts
- Add 1 task definition in `tasks.yaml` with prompt

**Non-Goals:**
- Afferent coupling (Ca) — who depends on this class? Requires reverse index, not available from single-module analysis
- Cyclic dependency detection — requires full dependency graph
- Runtime coupling — only static import-level coupling

## Decisions

### Decision 1: Coupling computed at @Tool time, not stored

**Chosen**: Coupling is computed in the `getCouplingReport` method by iterating each class's parent `BuildUnit` imports and counting types outside the class's own package. No new field on `Clazz.java` needed — avoids polluting the serialized model.

### Decision 2: Module dependency map returned alongside distributions

**Chosen**: The tool returns a `Map<String,Integer>` of module-to-dependency-count as part of the result. This lets the LLM see not just class-level coupling distribution but also which external modules are most depended-upon. Implemented as additional `Distribution` entries.

### Decision 3: Same-package types excluded from coupling count

**Chosen**: Types in the same package as the class are not counted as external dependencies. Only imports outside the class's package contribute to coupling. This matches standard Ce (Efferent Coupling) definition.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Imports include java.lang implicitly | `java.lang` imports are typically not in the import list (implicit). If present, they're filtered as external but negligible. |
| Module boundaries may be fuzzy | Use package -> module mapping from existing module/packages hierarchy |
| Import may resolve to class in same module but different package | Correctly counted as external coupling — matches standard Ce definition |

## Open Questions

*(None)*