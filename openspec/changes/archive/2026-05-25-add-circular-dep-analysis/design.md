## Context

The project has per-class coupling (Ce) computing from `BuildUnit` imports. The same import data can be used to build a directed dependency graph and detect cycles using Tarjan's SCC algorithm. No data model changes needed — only a new @Tool method and prompt.

## Goals / Non-Goals

**Goals:**
- Add 1 `@Tool`: `java_get_circular_dependency_report` using Tarjan's SCC on the import graph
- Return list of cycles with classes involved and cycle length
- Add 1 task in `tasks.yaml` with prompt

**Non-Goals:**
- Cross-module cycles (same-module only)
- Runtime cycle detection (static imports only)
- Automatic cycle-breaking suggestions

## Decisions

### Decision 1: Tarjan's SCC algorithm

**Chosen**: Standard Tarjan's algorithm on directed graph where nodes = classes and edges = imports (A imports B → edge A→B). Any SCC with size > 1 contains a cycle. O(V+E) complexity.

### Decision 2: Same-module scope only

**Chosen**: Only classes within the same build module are considered. Cross-module imports are excluded from cycle detection since they cross compilation boundaries and are harder to fix.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Large modules may have many cycles | Algorithm completes in O(V+E). Cycles reported grouped by length. |
| False positives via transitive deps | Tarjan's SCC finds all cycles — LLM can prioritize by cycle length |
| Import from same package not listed | Only imports in `BuildUnit.getImports()` are used; same-package refs excluded, but cycles via those would not be detected. Mitigation: add same-package type references if needed. |

## Open Questions

*(None)*