# batch-java-metric-evaluation Specification

## Purpose
TBD - created by archiving change batch-java-module-evaluation. Update Purpose after archive.
## Requirements
### Requirement: All Java metrics get batch evaluation support
The system SHALL provide batch evaluation support for every existing Java metric evaluation currently implemented as a per-module loop.

#### Scenario: All Java metric evaluation tasks have batch counterparts
- **WHEN** the batch Java metric capability is implemented
- **THEN** batch evaluation tasks exist for cyclomatic complexity, visibility, inheritance, method complexity, nesting depth, field visibility, class cohesion, coupling, test coverage, circular dependency, method count, documentation ratio, data class detection, boolean parameter analysis, annotation usage, package tangle detection, and import diversity

### Requirement: Batch metric report tools
The system SHALL provide one all-module Java metric report tool for each Java metric evaluation.

#### Scenario: Batch metric tool reads all raw module reports
- **WHEN** a batch metric report tool is called
- **THEN** it reads existing `Java_<moduleName>.json` raw module report files for all Java modules

#### Scenario: Batch metric tool returns compact data
- **WHEN** a batch metric report tool computes a metric
- **THEN** it returns compact metric distributions per module and does not return full JavaParser module objects

### Requirement: One batch LLM evaluation per metric
The system SHALL provide one non-loop LLM evaluation task per Java metric.

#### Scenario: Single LLM call evaluates all modules
- **WHEN** a batch metric evaluation task runs
- **THEN** it calls one batch metric report tool and produces findings for all modules in one LLM response

#### Scenario: Batch metric task depends on batch report collection
- **WHEN** a batch metric evaluation task is scheduled
- **THEN** it depends on `module_analysis_reports_all`

### Requirement: Batch evaluation result shape
The system SHALL use a top-level non-loop result shape for batch metric evaluations.

#### Scenario: Batch evaluation stores module evaluations
- **WHEN** a batch metric evaluation task succeeds
- **THEN** it stores a top-level result containing `reasoning` and `moduleEvaluations[]`

#### Scenario: Module evaluation entry
- **WHEN** a module has findings for a metric
- **THEN** `moduleEvaluations[]` contains an entry with `moduleName`, `reasoning`, and `evaluations`

### Requirement: Grouped findings list affected modules explicitly
The system SHALL require batch evaluation prompts to group findings common to multiple modules and explicitly list all affected modules.

#### Scenario: Common finding grouped once
- **WHEN** the same finding applies to multiple modules
- **THEN** the LLM response groups it once instead of repeating the same finding for every module

#### Scenario: Affected modules explicitly listed
- **WHEN** a grouped finding applies to modules `core`, `api`, and `web`
- **THEN** the finding text explicitly lists those modules, for example `Affected modules: core, api, web.` or `All modules are affected: core, api, web.`

#### Scenario: Vague grouped finding rejected by prompt guidance
- **WHEN** a finding would otherwise say `several modules` or `many modules`
- **THEN** the prompt instructs the LLM to replace it with an explicit affected-module list

### Requirement: No initial per-module finding limit
The system SHALL not enforce a fixed maximum number of findings per module in the initial batch metric evaluation implementation.

#### Scenario: No finding limit configured
- **WHEN** a batch metric evaluation task runs
- **THEN** it does not truncate findings based on a per-module maximum

### Requirement: Batch tasks active by default
The system SHALL mark batch Java metric evaluation tasks active by default in the software-architecture task pipeline.

#### Scenario: Batch metric tasks active
- **WHEN** the analysis pipeline is initialized after implementation
- **THEN** batch Java metric evaluation tasks are active by default

#### Scenario: Old per-module tasks disabled after verification
- **WHEN** batch report collection and batch metric evaluation are verified
- **THEN** the old per-module Java metric loop tasks are disabled or removed according to the migration plan

### Requirement: Existing Java metric code remains fallback
The system SHALL keep existing per-module Java metric report tools and evaluation logic available as fallback until batch migration is complete.

#### Scenario: Per-module tool remains available
- **WHEN** an existing per-module Java metric tool is called
- **THEN** it continues to return metric data for one module

### Requirement: Preserve CSV report side effects
The system SHALL preserve existing CSV report side effects when converting Java metric tools to batch variants.

#### Scenario: Module-specific CSV report preserved
- **WHEN** a batch metric tool writes a module-specific CSV report
- **THEN** it uses the existing `<ReportType>/<sanitizedModuleName>.csv` naming convention

#### Scenario: Global CSV report preserved
- **WHEN** a batch metric tool writes a global CSV report
- **THEN** it preserves the existing global report filename and directory behavior

### Requirement: Documentation renders batch evaluations
The system SHALL update analysis documentation rendering to display batch metric evaluation results.

#### Scenario: Batch evaluation section rendered
- **WHEN** analysis documentation is generated and a batch metric evaluation contains `moduleEvaluations[]`
- **THEN** the documentation renders each module evaluation and its findings

### Requirement: Non-Java module loops remain unchanged
The system SHALL not convert non-Java or interpretation-heavy module loops as part of this batch Java metric capability.

#### Scenario: Non-Java loops remain per module
- **WHEN** the batch Java metric capability is implemented
- **THEN** `ProgrammingLanguages`, `ModuleBuildfileAnalysis`, `ModulePurpose`, `ModuleArchitecture`, and `ModuleSubdirectories` remain per-module tasks unless changed by a separate capability

