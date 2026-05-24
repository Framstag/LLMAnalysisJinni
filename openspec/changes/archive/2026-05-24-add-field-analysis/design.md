## Context

The data model currently captures methods with full detail (visibility, modifiers, parameters, complexity, nesting) but has zero field information. Fields are half the class — they define state and are essential for evaluating encapsulation (public fields), data classes (fields + getters/setters only), god classes (many fields + many unrelated methods), and LCOM (do methods share fields?).

Implementation reuses the established patterns from the method analysis changes: add model → populate in parsers → expose via @Tool → evaluate in tasks.

## Goals / Non-Goals

**Goals:**
- Add `Field.java` model with name, type (FQN), visibility, isStatic, isFinal
- Add `fields` list to `Clazz.java`
- Populate fields in `JavaFileParser.java` (from `FieldDeclaration` AST) and `ClassFileParser.java` (from `classModel.fields()`)
- Add 2 `@Tool` methods: `java_get_field_visibility_report` and `java_get_class_cohesion_report`
- Add 2 task definitions in `tasks.yaml` with prompts
- Maintain backward compatibility

**Non-Goals:**
- Cross-class LCOM (only within-class cohesion)
- Field initialization analysis (assignments, constructor init)
- Field mutability tracking (final covers this)
- Type resolution beyond FQN from parser

## Decisions

### Decision 1: Field model mirrors Method pattern

**Chosen**: `Field.java` uses `MethodVisibility` enum (reuse), `type` as fully-qualified String, and `isStatic`/`isFinal` booleans. Uses Jackson annotations following the same pattern as `Method.java`.

### Decision 2: Multi-declaration fields expanded per variable

**Chosen**: A single `int x, y, z;` declaration creates 3 Field objects. JavaParser's `FieldDeclaration` has multiple variables in its `getVariables()` list. Each gets its own Field with the shared type.

### Decision 3: LCOM heuristic is method-to-field-access ratio, not full LCOM*

**Chosen**: Instead of computing LCOM (which requires tracking which methods access which fields), use a simpler heuristic: compare methods count vs fields count, and methods-that-access-fields vs total-methods ratio. Full LCOM would require per-method field-access tracking, which is significantly more complex. The heuristic catches the same patterns (lots of fields + lots of methods with no field access = likely low cohesion).

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| LCOM heuristic not as precise as full LCOM | LLM can still identify obvious god classes from field count + method count extremes |
| Fields from `.class` files have less type info | Fully-qualified type names available from bytecode; matches method approach |
| `Enum` class fields (implicit values) may confuse | Only explicit field declarations are captured; enum constants are implicit |

## Open Questions

*(None)*