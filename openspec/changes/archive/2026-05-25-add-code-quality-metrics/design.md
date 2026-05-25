## Context

Four lightweight metrics that require no data model changes. All data already exists on `Method.java`, `Clazz.java`, and `Field.java`. Each metric is a standalone `@Tool` method returning `Distribution` objects, following the established pattern.

## Goals / Non-Goals

**Goals:**
- Method count per class distribution
- Documentation ratio (commented vs uncommented classes/methods)
- Data class detection (field visibility + getter/setter pattern matching)
- Boolean parameter abuse (3+ boolean params via descriptor analysis)
- 4 @Tool methods, 4 prompts, 4 tasks, 4 doc sections

**Non-Goals:**
- Data model changes — all computed from existing properties
- Method-level documentation quality (has javadoc vs has any comment)
- Test assertion analysis

## Decisions

### Decision 1: Data class = all fields private OR all fields public + only getters/setters

**Chosen**: A class is flagged as data class if:
- All fields are private, and all methods are either getters (`getX`), setters (`setX`), or constructors, OR
- All fields are public, and there are no business logic methods (only language-level methods)
This avoids false positives for classes that genuinely need fields + accessors.

### Decision 2: Boolean params detected via descriptor string

**Chosen**: Method descriptors contain type codes — boolean is `Z`. A method with 3+ `Z` entries in its descriptor is flagged. Simple, fast, no type resolution needed.

### Decision 3: Documentation = has non-empty documentation string

**Chosen**: A class/method is "documented" if its `getDocumentation()` returns a non-null, non-empty string. No check for content quality.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Data class heuristic may have false positives | LLM can evaluate context — the tool flags candidates, LLM judges |
| Descriptor-based boolean count may include return type `boolean` | Only count `Z` in parameter portion of descriptor (between parentheses) |
| Documentation may be auto-generated | LLM can distinguish meaningful from auto-generated comments |

## Open Questions

*(None)*