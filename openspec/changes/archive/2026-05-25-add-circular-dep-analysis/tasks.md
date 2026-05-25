## 1. New @Tool Method

- [x] 1.1 Implement `java_get_circular_dependency_report` in `JavaTool.java` — build import-based directed graph per module, run Tarjan's SCC, return list of cycles with class names and cycle length
- [x] 1.2 Add `Cycle` inner class or simple data structure for cycle representation (list of class names + length)

## 2. Prompt

- [x] 2.1 Create prompt `prompts/module_circular_dependency_evaluation.md` — instruct LLM to evaluate cycles, flag large cycles (>3 classes) as critical

## 3. Task Pipeline

- [x] 3.1 Add `ModuleCircularDependencyEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_circular_dependency_report`

## 4. Documentation

- [x] 4.1 Add circular dependency evaluation section to `Documentation.md.hbs` — follow existing evaluation table pattern, use `circularDependencyEvaluation` response property

## 5. Build and Verify

- [x] 5.1 Build with `mvn verify -DskipTests` — confirm compilation
- [x] 5.2 Run full test suite with `mvn verify` — confirm no regressions