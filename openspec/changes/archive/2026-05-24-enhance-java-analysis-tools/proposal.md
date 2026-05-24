## Why

Current Java analysis tools capture structural metadata (class names, method names, cyclomatic complexity) but miss key OOP design quality dimensions — encapsulation (method visibility), inheritance structure, and method complexity beyond CC (parameter count, method length). Adding these enables the LLM to evaluate code quality patterns like anemic domain models, god classes, deep hierarchies, and method-level design smells — with minimal code changes (~50 lines of Java).

## What Changes

- Add `visibility`, `static`, `final` fields to `Method.java` model
- Add `superClass`, `interfaces` fields to `Clazz.java` model
- Add `parameterCount`, `linesOfCode` fields to `Method.java` model
- Populate new fields during `.java` file parsing in `JavaFileParser.java` and `.class` file parsing in `ClassFileParser.java`
- Add 3 new `@Tool` methods in `JavaTool.java` for LLM-accessible reports
- Add 3 new analysis tasks in `tasks.yaml` (prompts + JSON schemas) to evaluate each dimension
- No breaking changes to existing data model or task pipeline

## Capabilities

### New Capabilities

- `java-method-visibility-analysis`: Evaluates method visibility distribution (public/private/protected/package) per module, plus static/final modifier counts. Enables LLM to detect encapsulation leaks, anemic domain models, and overuse of static methods.
- `java-class-inheritance-analysis`: Evaluates class hierarchy depth, interface usage count, and superclass distribution per module. Enables LLM to detect deep inheritance chains, interface pollution, and missing abstraction layers.
- `java-method-complexity-analysis`: Evaluates method parameter count distribution and method length (lines of code) per module, cross-referenced with cyclomatic complexity. Enables LLM to detect god methods, excessive parameter lists, and high-priority refactoring targets.

### Modified Capabilities

- *(None — existing specs unchanged)*

## Impact

- **Data model**: `Method.java` — add 5 fields. `Clazz.java` — add 2 fields. No removal or rename of existing fields.
- **Parsers**: `JavaFileParser.java` — populate new Method/Clazz fields from JavaParser AST. `ClassFileParser.java` — populate new Clazz fields from `.class` bytecode metadata (already parsed but discarded).
- **Tools**: `JavaTool.java` — add 3 new `@Tool` methods returning `Distribution` objects.
- **Pipeline**: `tasks.yaml` — add 3 new tasks looping over modules, depending on `module_analysis_reports` tag.
- **No new dependencies** — reuses existing JavaParser and `java.lang.classfile` APIs already in the project.