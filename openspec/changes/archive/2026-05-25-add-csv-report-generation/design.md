## Context

`SBOMTool.java` writes `DependenciesAndLicenses.csv` using `CsvWriter` from `de.siegmar.fastcsv.writer`. The `AnalysisContext` is already injected into `JavaTool.java` via its constructor. Each `@Tool` method needs a CSV-writing counterpart that writes sorted raw data to `context.getWorkingDirectory()`.

## Goals / Non-Goals

**Goals:**
- All analysis `@Tool` methods in `JavaTool.java` write a CSV report file alongside their Distribution/List return
- CSV contains sorted raw data (e.g., class-level values, not just aggregated distributions)
- Filenames derived from tool name, e.g., `VisibilityDistribution.csv`

**Non-Goals:**
- Changing existing return types or LLM-facing behavior
- Generating CSV for non-Java tool methods
- Site-specific configuration of which CSVs to generate

## Decisions

### Decision 1: CSV writing happens inside each @Tool method

**Chosen**: After computing distributions, each method writes its CSV file before returning. This keeps the logic co-located and guarantees CSV is always written when the tool is called.

### Decision 2: Filename derived from tool name

**Chosen**: `getVisibilityDistributionReport` → `VisibilityDistribution.csv`, `getCouplingReport` → `Coupling.csv`, etc. Consistent, predictable, no config needed.

### Decision 3: Raw data sorted by relevant column

**Chosen**: CSV data sorted by the most relevant metric column (descending for metrics like coupling, CC; alphabetical for names). Makes the file human-readable immediately.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Many CSV files in working directory | Each tool call produces one file; typical run produces 5-15 files. Names are self-documenting. |
| CSV writing adds latency | Write is bounded by O(N) where N = number of classes/methods — negligible vs LLM call time |

## Open Questions

*(None)*