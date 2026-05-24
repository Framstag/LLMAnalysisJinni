## Why

Cyclomatic complexity (CC) counts decision paths in a method but does not measure readability. A method with CC=12 and nesting depth 2 (many flat conditions) is far more readable than a method with CC=8 and nesting depth 6 (if-in-for-in-if-in-while spaghetti). Adding nesting depth gives the LLM a second dimension of complexity — distinguishing "complex but flat" from "nested spaghetti" — with a trivial code change (one field, one parser line).

## What Changes

- Add `nestingDepth` field (int) to `Method.java`
- Populate in `JavaFileParser.java` by traversing AST body and tracking max nesting of if/for/while/switch/with/try blocks
- Populate in `ClassFileParser.java` — not applicable (bytecode has no source structure), leave as 0
- Add 1 new `@Tool` method in `JavaTool.java` returning nesting depth distribution per module
- Add 1 new analysis task in `tasks.yaml` for LLM evaluation of nesting depth distribution
- No breaking changes to existing data model or task pipeline

## Capabilities

### New Capabilities

- `java-method-nesting-analysis`: Evaluates method nesting depth distribution per module (prod/test/gen). Enables LLM to distinguish between "flat but complex" methods and "deeply nested spaghetti" methods, and flag deeply nested methods as readability refactoring targets.

### Modified Capabilities

- *(None — new standalone capability)*

## Impact

- **Data model**: `Method.java` — add 1 int field (`nestingDepth`), defaults to 0
- **Parsers**: `JavaFileParser.java` — populate via AST traversal of method body. `ClassFileParser.java` — unchanged (bytecode has no nesting depth)
- **Tools**: `JavaTool.java` — add 1 new `@Tool` method returning `List<Distribution>` objects
- **Pipeline**: `tasks.yaml` — add 1 new task looping over modules, depending on `module_analysis_reports` tag
- **No new dependencies** — reuses existing JavaParser APIs