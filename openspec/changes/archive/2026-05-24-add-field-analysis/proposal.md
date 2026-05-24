## Why

The current data model captures methods in full detail (visibility, parameters, complexity, nesting) but captures nothing about fields — despite fields being half the class definition. Without field data, the LLM cannot evaluate data class smells (all fields public + only getters/setters), god class smells (many fields + many methods, unrelated), LCOM (whether methods share fields), or field-level encapsulation. Adding a `Field.java` model completes the class picture and unlocks 3+ new analysis dimensions.

## What Changes

- Add `Field.java` model with name, type (fully-qualified), visibility, isStatic, isFinal
- Add `fields` list to `Clazz.java`
- Populate fields in `JavaFileParser.java` (from JavaParser AST) and `ClassFileParser.java` (from bytecode)
- Add 2 new `@Tool` methods in `JavaTool.java`:
  - `java_get_field_visibility_report` — field visibility/static/final distribution
  - `java_get_class_cohesion_report` — field count, method-to-field ratio, LCOM heuristic
- Add 2 new analysis tasks in `tasks.yaml` for LLM evaluation of field patterns and class cohesion
- No breaking changes to existing data model or task pipeline

## Capabilities

### New Capabilities

- `java-field-analysis`: Evaluates field visibility distribution and class cohesion metrics per module. Enables LLM to detect data classes (public fields + only getters/setters), god classes (many fields + many unrelated methods), field encapsulation leaks, and LCOM (Lack of Cohesion of Methods) heuristics.

### Modified Capabilities

- *(None — new standalone capability)*

## Impact

- **Data model**: Add `Field.java` (5 fields), add `List<Field>` to `Clazz.java`
- **Parsers**: `JavaFileParser.java` — extract field declarations from AST. `ClassFileParser.java` — extract fields from bytecode
- **Tools**: `JavaTool.java` — add 2 new `@Tool` methods returning `List<Distribution>` objects
- **Pipeline**: `tasks.yaml` — add 2 new tasks looping over modules
- **No new dependencies** — reuses existing JavaParser and `java.lang.classfile` APIs