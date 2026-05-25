## 1. Data Model

- [x] 1.1 Add `efferentCoupling` (int, default 0) field with getter/setter to `Clazz.java`

## 2. Coupling Computation in Report Generation

- [x] 2.1 In `JavaFileParser.java` or `ModuleManager.java`, compute efferent coupling per class — iterate imports on the `BuildUnit`, filter out same-package types, count remaining as coupling

## 3. New @Tool Method

- [x] 3.1 Implement `java_get_coupling_report` in `JavaTool.java` — for each class, read its `efferentCoupling`, return 3 `Distribution` objects (prod/test/gen) for class-level coupling, plus `Distribution` objects for module-level dependency counts

## 4. Prompt

- [x] 4.1 Create prompt `prompts/module_coupling_evaluation.md` — instruct LLM to evaluate coupling distribution, detect hub classes (Ce > 20), highly-coupled modules (mean Ce > 10), and dependency concentration

## 5. Task Pipeline

- [x] 5.1 Add `ModuleCouplingEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_coupling_report`

## 6. Documentation

- [x] 6.1 Add coupling evaluation section to `Documentation.md.hbs` — follow existing evaluation table pattern, use `couplingEvaluation` response property

## 7. Build and Verify

- [x] 7.1 Build with `mvn verify -DskipTests` — confirm compilation
- [x] 7.2 Run full test suite with `mvn verify` — confirm no regressions