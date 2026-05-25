## 1. Annotation Density Tool & Task

- [x] 1.1 Implement `@Tool` method `java_get_annotation_report` in `JavaTool.java` — iterates Clazz.annotations and Method.annotations, returns `List<Distribution>` with annotation type counts per prod/test/gen, plus @Override-specific count
- [x] 1.2 Write `AnnotationDensity.csv` via `CsvReportWriter` with per-class annotation counts (prod/test/gen sections, header-only if empty)
- [x] 1.3 Create prompt template `prompts/module_annotation_evaluation.md`
- [x] 1.4 Create JSON schema result file `results/ArchitectureEvaluation.json` (reuse existing — all eval tasks share this schema)
- [x] 1.5 Add task block to `tasks.yaml` with id `ModuleAnnotationEvaluation`, depends on `module_analysis_reports`, loops on modules
- [x] 1.6 Add documentation section in `Documentation.md.hbs` under "Analysis of Annotation Usage per Module"
- [x] 1.7 Update analysis README with new task row

## 2. Package Tangle Detection Tool & Task

- [x] 2.1 Implement `@Tool` method `java_get_package_tangle_report` in `JavaTool.java` — builds package-level dependency graph from BuildUnit.imports, runs Tarjan's SCC, returns list of package cycles
- [x] 2.2 Write `PackageTangles.csv` via `CsvReportWriter` with detected package cycles and contributing class imports
- [x] 2.3 Create prompt template `prompts/module_package_tangle_evaluation.md`
- [x] 2.4 Add task block to `tasks.yaml` with id `ModulePackageTangleEvaluation`, depends on `module_analysis_reports`, loops on modules
- [x] 2.5 Add documentation section in `Documentation.md.hbs` under "Analysis of Package Tangles per Module"
- [x] 2.6 Update analysis README with new task row

## 3. Import Diversity Tool & Task

- [x] 3.1 Implement `@Tool` method `java_get_import_diversity_report` in `JavaTool.java` — extracts package prefixes from BuildUnit.imports, categorizes as external/internal, returns `List<Distribution>` with prefix counts and external-to-internal ratio
- [x] 3.2 Write `ImportDiversity.csv` via `CsvReportWriter` with per-package import source counts (external/internal split)
- [x] 3.3 Create prompt template `prompts/module_import_diversity_evaluation.md`
- [x] 3.4 Add task block to `tasks.yaml` with id `ModuleImportDiversityEvaluation`, depends on `module_analysis_reports`, loops on modules
- [x] 3.5 Add documentation section in `Documentation.md.hbs` under "Analysis of Import Diversity per Module"
- [x] 3.6 Update analysis README with new task row