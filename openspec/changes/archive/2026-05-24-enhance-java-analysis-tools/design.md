## Context

Current `JavaTool.java` has 2 `@Tool` methods: `generateModuleAnalysisReport` (raw structural data) and `getCyclomaticComplexityModuleReport` (CC distribution). The data model captures class/method names, annotations, documentation, and CC — but not visibility, inheritance, or method-level complexity beyond CC.

The 3 new capabilities target OOP design quality dimensions the LLM can evaluate with small data additions. All changes slot into the existing pattern: enhance data model → populate in parsers → expose via `@Tool` returning `Distribution` objects → evaluate in task prompts.

## Goals / Non-Goals

**Goals:**
- Add `visibility`, `static`, `final`, `parameterCount`, `linesOfCode` to `Method.java`
- Add `superClass`, `interfaces` to `Clazz.java`
- Populate new fields in `JavaFileParser.java` (from JavaParser AST) and `ClassFileParser.java` (from bytecode, already parsed but discarded)
- Add 3 `@Tool` methods in `JavaTool.java` returning `List<Distribution>` objects
- Add 3 task definitions in `tasks.yaml` with prompts + JSON schemas
- Maintain backward compatibility: existing fields, tools, and tasks unchanged

**Non-Goals:**
- Cross-module analysis (Ca/Ce coupling, LCOM) — requires full dependency graph
- Field-level analysis — adds complexity without clear LLM evaluation path
- New Maven dependencies — all APIs used (`com.github.javaparser`, `java.lang.classfile`) already in project

## Decisions

### Decision 1: Custom Java enum for method visibility

**Option**: Use JavaParser's `AccessSpecifier` enum, use plain String, or define a custom Java enum.

**Chosen**: Custom `MethodVisibility` enum with values `PUBLIC`, `PROTECTED`, `PACKAGE_PRIVATE`, `PRIVATE`. Provides type safety, clean JSON serialization via Jackson, and avoids coupling to JavaParser's internal enum. Define as standalone file `MethodVisibility.java` in `tools/java/` package.

### Decision 2: Inheritance depth computed in @Tool, not stored in model

**Option**: Precompute depth when building Clazz vs compute when generating report.

**Chosen**: Compute in `@Tool`. Depth requires traversing superclass chain (recursive lookup across the module). Precomputing would require two-pass parsing. Computing at report time is simpler and keeps parsing stateless.

### Decision 3: Interface count distribution per class, not per BuildUnit

**Option**: Count interfaces per BuildUnit (file-level) vs per Clazz (class-level).

**Chosen**: Per Clazz. More granular. Detects "single class implements 12 interfaces" smells. BuildUnit can compute aggregate from its Clazzes.

### Decision 4: Three separate @Tool methods, not one combined

**Option**: Single `getJavaMetricsReport` tool vs 3 targeted tools.

**Chosen**: 3 separate tools. Follows existing pattern (`getCyclomaticComplexityModuleReport` is single-purpose). Makes tool whitelisting per task clean. LLM calls only what it needs.

### Decision 5: Lines of code = body statement count, not source lines

**Option**: Count actual source lines vs count AST statements.

**Chosen**: AST statement count. Deterministic, independent of formatting. Matches how CC is computed (AST-based). Avoids edge cases with blank lines, comments, formatting.

### Decision 6: All type references stored as fully-qualified names

**Option**: Store simple class names vs fully-qualified names for `superClass` and `interfaces`.

**Chosen**: Fully-qualified names (`java.lang.String`, not `String`). Enables deduplication across packages — same type in different modules resolves to same qualified name. Matches existing pattern in `imports` on `BuildUnit`. Both JavaParser and `java.lang.classfile` APIs already return qualified names.

### Decision 7: visibility defaults to null

**Chosen**: `visibility` field defaults to `null`. Filtered from distributions in @Tool methods. LLM handles gaps.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| `linesOfCode` from `.class` files not available (bytecode has no line count info) | `linesOfCode` left as `null` for class file parsing; only populated from `.java` source. Similar to CC which is also source-only. |
| `superClass` resolution fails for classes outside source tree | Store raw string from parser; don't require resolution via TypeSolver. ClassFileParser already gets it from bytecode. |
| Interface count may include java.lang interfaces (Serializable, Cloneable) | LLM can distinguish in evaluation; raw data includes full qualified names. |
| 3 new tasks increase total LLM calls per module | Each task is optional (active: true/false). Users disable what they don't need. Total runtime increase ~30-60s per module. |

## Open Questions

