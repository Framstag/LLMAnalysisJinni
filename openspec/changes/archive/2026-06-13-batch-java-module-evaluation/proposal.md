# Batch Java Module Evaluation

## Why

The software-architecture analysis currently loops over every build module for Java report generation and Java metric evaluation. For projects with 150-200 modules, this creates many repeated LLM calls even when the underlying work is deterministic tool execution over already-discovered modules.

This change introduces an all-module batch path for Java module reports and Java metric evaluations, reducing repeated LLM orchestration while preserving deterministic Java-side computation.

## What Changes

- Add an all-module Java raw report collection capability.
  - Replaces the per-module `ModuleAnalysisReports` loop with a non-loop task.
  - Java code iterates all detected Java modules.
  - Existing `Java_<moduleName>.json` raw module report files remain the source of truth.

- Add all-module Java metric report tools.
  - Each metric gets a batch variant that loads existing raw module reports and returns compact metric data for all modules.
  - Existing per-module metric tools remain available unless later explicitly deprecated.

- Add all-module Java metric evaluation tasks.
  - Each batch metric evaluation task calls one batch report tool.
  - One LLM call evaluates all modules for that metric.
  - The LLM returns findings for all modules in one JSON response.
  - Findings common to multiple modules should be grouped once and must explicitly list all affected modules.

- Add a batch-friendly result shape for module evaluations.
  - Proposed shape: `moduleEvaluations[]`, where each entry contains `moduleName`, `reasoning`, and `evaluations`.
  - Documentation rendering must support this shape, or a small engine change must write batch results back into each module entry.

- Keep non-Java and interpretation-heavy module loops unchanged.
  - `ProgrammingLanguages`, `ModuleBuildfileAnalysis`, `ModulePurpose`, `ModuleArchitecture`, and `ModuleSubdirectories` remain per-module unless separately redesigned.
  - `InterModuleDependencyEvaluation` remains unchanged because it already follows the all-module pattern.

## Capabilities

### New Capabilities

- `batch-java-report-collection`: Generates Java raw module analysis reports for all Java modules in one non-loop task. The capability owns the all-module Java report collection task, Java-side module iteration, report descriptors, skipped-module reporting, and resumability behavior for existing `Java_<moduleName>.json` files.

- `batch-java-metric-evaluation`: Provides all-module batch report tools and all-module LLM evaluation tasks for Java metrics. The capability covers compact all-module metric data, batch evaluation prompts, batch evaluation schemas, and documentation rendering for all-module findings.

### Modified Capabilities

- None in the initial additive implementation. Existing per-module Java metric capabilities remain valid. If old per-module tasks are removed instead of kept alongside batch tasks, affected Java metric specs will need delta specs.

## Impact

Affected areas:

- `src/main/java/com/framstag/llmaj/tools/java/JavaTool.java`
  - Add all-module report generation.
  - Add batch metric report tools.
  - Reuse existing raw module report parsing and cache behavior.

- `analysis/software-architecture/tasks.yaml`
  - Add `CollectJavaModuleReportsAll`.
  - Add batch metric evaluation tasks.
  - Disable or replace the existing `ModuleAnalysisReports` loop after verification.
  - Optionally disable per-module Java metric loops after batch tasks are verified.

- `analysis/software-architecture/prompts/`
  - Add all-module report collection prompt.
  - Add all-module metric evaluation prompts.

- `analysis/software-architecture/results/`
  - Add schemas for all-module report descriptors and all-module evaluation responses.

- `analysis/software-architecture/documentation/Documentation.md.hbs`
  - Update rendering if batch evaluation uses `moduleEvaluations[]`.

- Existing OpenSpec specs
  - New specs are expected for `batch-java-report-collection` and `batch-java-metric-evaluation`.
  - Existing Java metric specs are not changed in the additive phase.
