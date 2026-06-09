## 1. Java Tool — `java_get_inter_module_dependency_report`

- [x] 1.1 Add `getInterModuleDependencyReport()` method to `JavaTool.java` with `@Tool` annotation
- [x] 1.2 Implement namespace index builder from module class qualifiedNames
- [x] 1.3 Implement import classifier with longest-prefix module matching
- [x] 1.4 Implement `findTargetModule()` private helper for single import resolution
- [x] 1.5 Implement Ce-inter, Ca, Instability computation
- [x] 1.6 Implement `writeDependencyMatrixCsv()` for CSV export

## 2. Evaluation Task — `InterModuleDependencyEvaluation`

- [x] 2.1 Create evaluation prompt `inter_module_dependency_evaluation.md`
- [x] 2.2 Add task entry in `analysis/software-architecture/tasks.yaml`
- [x] 2.3 Whitelist `java_get_inter_module_dependency_report` tool for the task

## 3. Spec & Documentation

- [x] 3.1 Create spec at `openspec/specs/inter-module-dependency-analysis/spec.md`
- [x] 3.2 Add inter-module section to `Documentation.md.hbs`
- [x] 3.3 Update README.md task table with new task entry

## 4. Verification

- [x] 4.1 `mvn compile` succeeds
- [x] 4.2 `mvn test` passes all existing tests
- [x] 4.3 Run full analysis on a test project to verify tool output