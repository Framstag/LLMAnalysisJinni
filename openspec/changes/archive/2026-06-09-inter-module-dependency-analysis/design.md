## Context

The system currently analyzes each build module independently using JavaParser-generated reports. Each module's report contains full class, method, field, and import data. The coupling tool (`java_get_coupling_report`) tracks efferent coupling (Ce) per class and module-level external dependency counts — but uses the first 2 package-name segments as the grouping key, which cannot distinguish between other project modules and third-party libraries.

To compute inter-module dependencies, we need to:

1. Know which package namespaces belong to which module
2. Classify each import across all modules as either intra-module, inter-module (project), or third-party
3. Aggregate the results into a dependency matrix, fan-in (Ca), and Instability

## Goals / Non-Goals

**Goals:**

- Deterministic cross-module dependency analysis (no LLM guessing)
- Fan-in (Ca) per module: count of other project modules that depend on it
- Inter-module Ce per module: count of other project modules it depends on
- Instability = Ce(inter-module) / (Ce(inter-module) + Ca) computed automatically
- Dependency matrix as CSV export for downstream use
- LLM evaluation task that interprets the data and produces architecture findings

**Non-Goals:**

- Abstractness computation (A = abstract / total classes) — left for future work
- Distance from main sequence (D = |A + I - 1|) — requires Abstractness
- Real-time analysis — runs in the existing batch pipeline
- GUI or interactive visualization — CSV + doc report is sufficient

## Decisions

**Decision 1: New Java tool (not pure LLM reasoning)**

| Approach | Pros | Cons |
|----------|------|------|
| **Selected: New Java tool** | Deterministic, fast, reusable, feeds CSV to reports | ~237 lines Java |
| Pure LLM evaluation | Zero Java code | Fragile — LLM must guess which imports are intra-project |
| Hybrid (LLM + fact file) | Flexible | Extra maintenance burden |

The data (class qualified names, imports) is already available in module reports. A deterministic tool is far more reliable than LLM namespace classification.

**Decision 2: Namespace index from class qualifiedNames (not source tree scanning)**

- Class `qualifiedName` is already parsed and stored in each module's `Clazz` object
- Extracting the package from `qualifiedName` (trim after last dot) gives the file's package
- Source tree scanning would require additional filesystem I/O and path→package mapping

**Decision 3: Longest-prefix match for import classification**

- A module may have multiple packages (e.g., `com.framstag.core`, `com.framstag.core.service`)
- Simple `startsWith` could match the wrong module if namespaces overlap
- Longest-prefix match ensures `com.framstag.core.api.Spec` maps to module `core-api`, not `core`

**Decision 4: Instability encoded as integer 0-100**

- Distribution entries have `(String value, Integer count)` type constraint
- Using `count = ceil(Instability * 100)` loses precision but is sufficient for evaluation
- The value string includes both name and value: `"core=40"` for clarity

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| **False positives** — import matches wrong module due to namespace overlap | Longest-prefix match minimizes this. Falls to LLM evaluation to catch anomalies. |
| **Modules without JavaParser reports** (non-Java modules) | Tool skips modules that fail `getModuleReport()`. Logs warnings. |
| **Fewer than 2 modules** — analysis is meaningless | Tool returns early with empty result. |
| **JDK/internal imports leaking into counts** | Filter `java.`, `javax.`, `java.lang` early in the import loop. |
| **Static imports** | Filtered: `imp.startsWith("static ")`. |
| **Wildcard imports (`com.foo.*`)** | Handled: strip `.*`, compare package against known module packages. Handled for same-package and same-module cases. |