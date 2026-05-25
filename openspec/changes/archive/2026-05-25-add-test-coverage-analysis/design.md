## Context

The project has existing infrastructure separating test vs production code (`BuildUnit.isProduction()`). Each `BuildUnit` has a list of classes and a flag indicating whether it's a test or production source set. Test coverage can be computed by matching production class names to test class names using standard naming conventions (`FooTest`, `FooImplTest`).

## Goals / Non-Goals

**Goals:**
- Add 1 `@Tool`: `java_get_test_coverage_report` returning per-module statistics: total production classes, tested count, untested count, test-to-production ratio
- Add 1 task definition in `tasks.yaml` with prompt
- Match using naming conventions: `Foo` → `FooTest`, `FooImpl` → `FooImplTest`, `AbstractFoo` → skip (abstract classes may not need tests)

**Non-Goals:**
- Parse test framework annotations (`@Test`, `@SpringBootTest`)
- Detect integration vs unit tests
- Measure assertion count or test quality

## Decisions

### Decision 1: Naming convention matching only

**Chosen**: Match `ClassName` → `ClassNameTest` and `ClassNameTest` within the test BuildUnits of the same module. No annotation scanning or test-count analysis. Simple, fast, covers the 90% case.

### Decision 2: Report class-level, not method-level

**Chosen**: Reports whether a class HAS a corresponding test class, not how many test methods exist. Method-level coverage belongs to a different analysis scope.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Non-standard naming conventions miss some tests | LLM can note in evaluation; heuristic covers most projects |
| Inner classes and nested types may not match | Top-level class matching only; inner classes listed as untested |
| Test class may exist but test different logic | Naming convention is best-effort; LLM can flag anomalies |

## Open Questions

*(None)*