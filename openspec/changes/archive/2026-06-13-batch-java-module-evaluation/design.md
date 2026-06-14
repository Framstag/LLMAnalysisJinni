# Design: Batch Java Module Evaluation

## Context

The current software-architecture pipeline loops over `/modules/modules` for Java report generation and Java metric evaluation. `ModuleAnalysisReports` generates one raw Java module report per module, then each Java metric task loops over modules again and calls a metric-specific tool per module.

This works, but creates many LLM orchestration rounds for deterministic Java-side work. For large multi-module projects, the repeated pattern is:

```text
LLM call
  -> tool call for module N
  -> store result for module N
repeat for every module
```

The existing `InterModuleDependencyEvaluation` already proves the all-module pattern: it has no `loopOn`, calls `java_get_inter_module_dependency_report`, and evaluates all modules in one task.

## Goals / Non-Goals

**Goals:**

- Add an all-module Java raw report collection path.
- Add batch Java metric report tools that read existing raw module reports and return compact metric data for all modules.
- Add batch LLM evaluation tasks that produce findings for all modules in one response.
- Preserve existing raw Java module report files as the source of truth.
- Keep existing per-module Java metric capabilities available during rollout.
- Keep non-Java and interpretation-heavy module loops unchanged.

**Non-Goals:**

- Do not redesign `ProgrammingLanguages`, `ModuleBuildfileAnalysis`, `ModulePurpose`, `ModuleArchitecture`, or `ModuleSubdirectories`.
- Do not change the JavaParser report model.
- Do not change CSV report directory structure.
- Do not remove existing per-module metric tasks in the first implementation.
- Do not add Maven dependencies.

## Decisions

### Decision 1: Java-side iteration for report collection

The all-module raw report collection must run inside `JavaTool`, not by asking the LLM to call `java_generate_module_analysis_report` many times.

Reason:

- Java-side iteration is deterministic.
- The LLM may omit modules when asked to issue 150-200 tool calls.
- Existing report generation logic already knows how to locate modules, parse Java files, write `Java_<moduleName>.json`, and cache parsed modules.

Design:

```text
CollectJavaModuleReportsAll
  -> java_generate_all_module_analysis_reports()
  -> returns report descriptors
```

The batch method should:

- Read `analysisState.modules.modules`.
- Use `modules[i].programmingLanguages` to select Java modules.
- Reuse existing `Java_<moduleName>.json` files when present.
- Regenerate only when a raw report file is missing or explicitly requested by a future refresh option.
- Reuse existing report generation logic.
- Return descriptors for generated or reused reports.
- Return skipped modules with reasons.
- Avoid failing the whole task for one bad module when possible.

### Decision 2: Raw Java module reports remain the source of truth

Batch metric tools should not re-parse source files. They should load existing raw module reports:

```text
workingDirectory/Java_<moduleName>.json
```

Existing behavior already uses this pattern through `getModuleReport(moduleName)`.

This keeps metric tools fast and avoids duplicated JavaParser work.

### Decision 3: Batch metric tools return compact data only

Batch metric tools should return compact metric distributions for all modules, not full `Module` objects.

Example:

```json
[
  {
    "moduleName": "core",
    "distributions": [
      {
        "name": "Cyclomatic complexity per method",
        "entries": [
          { "value": "3", "count": 12 },
          { "value": "8", "count": 2 }
        ]
      }
    ]
  }
]
```

Reason:

- The LLM only needs summarized metric data for evaluation.
- Full AST/module objects would make prompts and responses much larger.
- Compact data reduces token cost and response-size risk.

### Decision 4: One batch task per metric

Each existing Java metric gets one all-module task. The first implementation should convert all Java metric evaluations, not only a pilot subset.

```text
CyclomaticComplexityEvaluationAll
VisibilityEvaluationAll
InheritanceEvaluationAll
MethodComplexityEvaluationAll
NestingDepthEvaluationAll
FieldVisibilityEvaluationAll
ClassCohesionEvaluationAll
CouplingEvaluationAll
TestCoverageEvaluationAll
CircularDependencyEvaluationAll
MethodCountEvaluationAll
DocumentationRatioEvaluationAll
DataClassEvaluationAll
BooleanParameterEvaluationAll
AnnotationEvaluationAll
PackageTangleEvaluationAll
ImportDiversityEvaluationAll
```

Each task:

- Depends on `module_analysis_reports_all`.
- Calls one batch metric report tool.
- Receives all modules and all metric data.
- Produces one `ArchitectureEvaluation`-compatible response using `moduleEvaluations[]`.

This matches the user's intended model:

```text
1 metric
  = 1 batch report tool
  = 1 LLM evaluation task
  = all module findings
```

### Decision 5: Keep existing top-level non-loop result structure

Batch evaluation responses should keep the existing top-level non-loop result structure:

```json
{
  "reasoning": "Overall reasoning for this metric across all modules.",
  "moduleEvaluations": [
    {
      "moduleName": "core",
      "reasoning": "Module-level reasoning.",
      "evaluations": [
        {
          "aspect": "...",
          "expectation": "...",
          "finding": "...",
          "recommendation": "...",
          "urgency": "MEDIUM",
          "criticality": "MEDIUM"
        }
      ]
    }
  ]
}
```

Reason:

- Current non-loop tasks write to top-level state only.
- Writing back into `modules[i].<property>` would require engine changes.
- `moduleEvaluations[]` keeps batch results self-contained and easy to render.

Grouped findings are required when one finding applies to multiple modules. The prompt must instruct the LLM to group the finding once and explicitly list every affected module. Acceptable wording patterns include:

- `All modules are affected: core, api, web.`
- `Affected modules: core, api, web.`
- `Modules with this finding: core, api, web.`

Do not use vague grouped findings such as `several modules` or `many modules`. The grouped finding must name the full affected module set.

Documentation must be updated to render this shape.

### Decision 6: Batch tasks are active by default

Batch tasks are active by default once implemented. Existing per-module Java metric code remains available as fallback, but the old per-module pipeline should be disabled after the batch path is verified.

Rollout:

```text
Phase 1:
  Add batch report collection, active by default.
  Add all batch metric tools.
  Add all batch metric evaluation tasks, active by default.
  Keep old per-module task definitions disabled or inactive after verification.

Phase 2:
  Verify batch report collection against existing per-module report generation.
  Verify batch metric output against existing per-module metric output.
  Disable `ModuleAnalysisReports` loop.
  Disable old per-module Java metric loops.

Phase 3:
  Remove old Java metric loop tasks only after verified.
```

This reduces risk while making the new batch path the default analysis path.

### Decision 7: Keep non-Java module loops unchanged

The following remain per-module:

- `ProgrammingLanguages`
- `ModuleBuildfileAnalysis`
- `ModulePurpose`
- `ModuleArchitecture`
- `ModuleSubdirectories`

Reason:

These tasks require LLM interpretation of file lists, build files, or architecture context. They are not simple deterministic report-generation tasks.

### Decision 8: Preserve existing CSV side effects

Some metric tools write CSV files, for example package tangles and import diversity. Batch variants must preserve these side effects.

For module-specific CSV reports, use existing module-name filename conventions:

```text
<ReportType>/<sanitizedModuleName>.csv
```

For global reports, preserve existing global naming.

## Risks / Trade-offs

[Risk] Batch LLM responses become too large.  
Mitigation: return compact distributions, evaluate one metric at a time, and prompt the LLM to group findings common to multiple modules into one finding that names the affected modules. No per-module finding limit is planned initially.

[Risk] Batch report generation is all-or-nothing.  
Mitigation: skip existing report files, return per-module skipped/error descriptors, and make the task idempotent.

[Risk] Documentation still expects per-module properties.  
Mitigation: update `Documentation.md.hbs` to render `moduleEvaluations[]`.

[Risk] Existing per-module tasks and new batch tasks produce duplicate work if both enabled.  
Mitigation: batch tasks are active by default; old per-module Java metric loops should be disabled after verification.

[Risk] Batch metric tools duplicate logic from existing per-module tools.  
Mitigation: extract shared helper methods in `JavaTool` so both per-module and batch tools call the same metric computation code.

[Risk] Some modules have no raw Java report.  
Mitigation: batch tools skip missing reports and include skipped-module descriptors.

[Risk] Batch evaluation may hide module-specific context.  
Mitigation: include module name, path, language, and concise module context in prompts.

## Migration Plan

1. Create OpenSpec specs:
   - `batch-java-report-collection/spec.md`
   - `batch-java-metric-evaluation/spec.md`

2. Add JavaTool batch methods:
   - `java_generate_all_module_analysis_reports`
   - `java_get_all_*_reports` for selected or all Java metrics.

3. Add analysis artifacts:
   - `prompts/collect_java_module_reports_all.md`
   - `prompts/*_all.md`
   - `results/ModuleAnalysisReportsAll.json`
   - `results/ModuleBatchEvaluation.json`

4. Add tasks:
   - `CollectJavaModuleReportsAll`
   - `*EvaluationAll` tasks for batch metrics.

5. Add documentation rendering for `moduleEvaluations[]`.

6. Verify:
   - `mvn verify`
   - Run analysis on a small multi-module project.
   - Compare batch output against existing per-module output for at least one metric.

7. Rollout:
   - Batch tasks are active by default.
   - Disable `ModuleAnalysisReports` after verification.
   - Disable old per-module Java metric loops after verification.
   - Keep old Java code as fallback until removal is safe.

Rollback:

```text
Re-enable old ModuleAnalysisReports and old per-module metric tasks.
Disable batch tasks.
Remove or ignore batch-generated top-level evaluation properties.
```

## Decisions Captured

- All Java metrics should be converted to batch evaluation tasks.
- Batch report collection should reuse existing `Java_<moduleName>.json` files by default.
- Batch evaluation should keep the existing top-level non-loop result shape, using `moduleEvaluations[]`.
- Batch tasks should be active by default.
- No per-module finding limit initially.
- Batch prompts should instruct the LLM to group findings common to multiple modules into one finding.
- Grouped findings must explicitly list all affected modules, for example `Affected modules: core, api, web.` or `All modules are affected: core, api, web.`

No remaining open questions.
