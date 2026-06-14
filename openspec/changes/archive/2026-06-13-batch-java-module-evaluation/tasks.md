## 1. Batch Java raw report collection

- [x] 1.1 Add `JavaTool.java_generate_all_module_analysis_reports` to iterate detected modules inside Java code
- [x] 1.2 Select Java modules from `analysisState.modules.modules` using detected programming languages
- [x] 1.3 Reuse existing `Java_<moduleName>.json` files by default and generate only missing raw reports
- [x] 1.4 Return generated, reused, skipped, and error report descriptors with module names and reasoning
- [x] 1.5 Expose `java_generate_all_module_analysis_reports` through `JavaTool` tool registration
- [x] 1.6 Add unit tests for Java selection, raw report reuse, missing report generation, and skipped non-Java modules

## 2. Batch Java metric data layer

- [x] 2.1 Add compact DTOs for all-module metric report results, including `moduleName` and metric distributions
- [x] 2.2 Add shared raw Java report loading helpers that read existing `Java_<moduleName>.json` files
- [x] 2.3 Extract shared metric computation helpers so per-module and batch tools use the same logic
- [x] 2.4 Add skipped-module descriptors for missing or unreadable raw reports
- [x] 2.5 Preserve existing CSV report directories and filenames for module-specific and global reports

## 3. Batch Java metric report tools

- [x] 3.1 Add `java_get_all_cyclomatic_complexity_reports`
- [x] 3.2 Add `java_get_all_visibility_distribution_reports`
- [x] 3.3 Add `java_get_all_inheritance_reports`
- [x] 3.4 Add `java_get_all_method_complexity_reports`
- [x] 3.5 Add `java_get_all_method_nesting_depth_reports`
- [x] 3.6 Add `java_get_all_field_visibility_reports`
- [x] 3.7 Add `java_get_all_class_cohesion_reports`
- [x] 3.8 Add `java_get_all_coupling_reports`
- [x] 3.9 Add `java_get_all_test_coverage_reports`
- [x] 3.10 Add `java_get_all_circular_dependency_reports`
- [x] 3.11 Add `java_get_all_method_count_reports`
- [x] 3.12 Add `java_get_all_documentation_ratio_reports`
- [x] 3.13 Add `java_get_all_data_class_reports`
- [x] 3.14 Add `java_get_all_boolean_parameter_reports`
- [x] 3.15 Add `java_get_all_annotation_reports`
- [x] 3.16 Add `java_get_all_package_tangle_reports`
- [x] 3.17 Add `java_get_all_import_diversity_reports`
- [x] 3.18 Expose all batch metric report tools through `JavaTool` `@Tool` registration
- [x] 3.19 Add unit tests comparing batch metric outputs with existing per-module metric outputs

## 4. Batch analysis prompts and schemas

- [x] 4.1 Add `results/ModuleAnalysisReportsAll.json` for batch raw report descriptors
- [x] 4.2 Add `prompts/collect_java_module_reports_all.md` to call `java_generate_all_module_analysis_reports` once
- [x] 4.3 Add `results/ModuleBatchEvaluation.json` for top-level `reasoning` and `moduleEvaluations[]`
- [x] 4.4 Add batch evaluation prompts for all Java metrics using one batch report tool per metric
- [x] 4.5 Add prompt guidance requiring grouped findings to explicitly list every affected module
- [x] 4.6 Add prompt guidance rejecting vague grouped wording such as `several modules` or `many modules`
- [x] 4.7 Ensure batch prompts request no initial per-module finding limit

## 5. Software architecture task pipeline

- [x] 5.1 Add `CollectJavaModuleReportsAll` to `analysis/software-architecture/tasks.yaml`
- [x] 5.2 Set `CollectJavaModuleReportsAll` active by default with no `loopOn`
- [x] 5.3 Make `CollectJavaModuleReportsAll` depend on `modules`, `programming_language`, and `module_subdirectories`
- [x] 5.4 Tag `CollectJavaModuleReportsAll` as `module_analysis_reports_all`
- [x] 5.5 Add `CyclomaticComplexityEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.6 Add `VisibilityEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.7 Add `InheritanceEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.8 Add `MethodComplexityEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.9 Add `NestingDepthEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.10 Add `FieldVisibilityEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.11 Add `ClassCohesionEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.12 Add `CouplingEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.13 Add `TestCoverageEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.14 Add `CircularDependencyEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.15 Add `MethodCountEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.16 Add `DocumentationRatioEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.17 Add `DataClassEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.18 Add `BooleanParameterEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.19 Add `AnnotationEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.20 Add `PackageTangleEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.21 Add `ImportDiversityEvaluationAll` and depend on `module_analysis_reports_all`
- [x] 5.22 Mark all batch metric evaluation tasks active by default
- [x] 5.23 Keep existing per-module Java metric tasks available as fallback until verified
- [x] 5.24 Disable old per-module Java report and metric loop tasks after batch verification

## 6. Documentation rendering and verification

- [x] 6.1 Update `analysis/software-architecture/documentation/Documentation.md.hbs` to render top-level `moduleEvaluations[]`
- [x] 6.2 Render grouped findings with explicit affected module lists in generated documentation
- [x] 6.3 Run `mvn verify`
- [x] 6.4 Run analysis on a small multi-module Java project
- [x] 6.5 Compare batch raw reports against existing per-module raw reports
- [x] 6.6 Compare at least one batch metric evaluation against the old per-module metric evaluation
- [x] 6.7 Verify generated documentation includes batch evaluation sections
