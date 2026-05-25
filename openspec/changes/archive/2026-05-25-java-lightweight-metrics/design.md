## Context

Three new Java analysis tools, evaluation tasks, prompts, schemas, and documentation sections. All data required is already captured during `ModuleAnalysisReports` (the `java_generate_module_analysis_report` tool). Zero parser changes. All tools follow established patterns in `JavaTool.java`.

Current data availability:
- `Clazz.annotations` — list of `Annotation` objects per class (captured by `ClassFileParser`/`JavaFileParser`)
- `Method.annotations` — list of `Annotation` objects per method
- `BuildUnit.imports` — list of import strings per build unit
- `Module.packages` → `Package.buildUnits` → `BuildUnit.imports` + `BuildUnit.clazzes`

## Goals / Non-Goals

**Goals:**
- Add 3 new `@Tool` methods to `JavaTool.java` (no new files, no new model classes)
- Add 3 new evaluation tasks to `tasks.yaml` (loopOn per module)
- Add 3 prompt templates under `prompts/`
- Add 3 JSON schema files under `results/`
- Add 3 documentation sections to `Documentation.md.hbs`
- Follow existing Distribution-based return type + CSV report conventions

**5. Per-tool CSV reports follow `csv-report-generation` spec**
- Every new `@Tool` method writes a CSV report file alongside returning `Distribution` objects
- CSV files named: `AnnotationDensity.csv`, `PackageTangles.csv`, `ImportDiversity.csv`
- Written via `CsvReportWriter.writeMultiMapCsv` / `writeMultiIntMapCsv` matching existing pattern
- Header-only CSV written when data is empty (consistency with existing behavior)

**Non-Goals:**
- No parser changes (ClassFileParser, JavaFileParser untouched)
- No new data model fields (Clazz, Method, Field, BuildUnit unchanged)
- No new dependencies
- No changes to plugin/macro/fact layer

## Decisions

**1. Reuse existing `Distribution` return type for all three tools**
- All existing analysis tools return `List<Distribution>` — consistency matters
- New tools return `List<Distribution>` too, with `Distribution` names clarifying context
- CSV reports written alongside via `CsvReportWriter`

**2. Package tangle detection reuses Tarjan's SCC approach from circular dependency code**
- Circular dependency tool already implements Tarjan's SCC at class level
- Package tangle tool builds a package-level graph by grouping class-level imports by package prefix
- Same algorithm, different graph granularity
- Alternative considered: extracting to shared utility — rejected for now (small scope)

**3. Annotation report merges class-level and method-level annotations into one Distribution**
- `Distribution` entries: annotation type names → counts
- Separate Distribution for @Override ratio (count of methods with @Override / total methods)
- Includes prod/test/gen separation like all other tools

**4. Import diversity uses package prefix extraction from import strings**
- Import strings follow `com.framstag.llmaj.tools.java.JavaTool` format
- Extract top-level package prefix (`com.framstag.*`, `org.springframework.*`, etc.)
- Count unique prefixes and their frequencies per module
- Flags ratio of "external" (outside project) vs "internal" (within project) imports

## Risks / Trade-offs

- **Annotation report only covers annotations parsed by JavaParser/ClassFileParser** — runtime-retained annotations are captured; source-only annotations (e.g., Lombok `@Setter` without retention) may be absent depending on parser configuration. → Mitigation: document this limitation in the prompt.
- **Package tangle detection uses simple package prefix matching** — may produce false positives if classes in same package are in different logical modules. → Mitigation: prompt directs LLM to cross-reference with module boundaries.
- **Import diversity relies on import statements only** — fully-qualified inline references are missed. → Mitigation: acceptable trade-off; imports cover the vast majority of type references in Java.
- **All three share module report loading pattern** — if module report isn't cached, each tool reads the JSON file from disk. → Mitigation: existing moduleMapCache handles this; no performance concern for analysis-scale usage.