## 1. New @Tool Method

- [x] 1.1 Implement `java_get_test_coverage_report` in `JavaTool.java` — iterate module classes, collect production classes, match each against test classes by naming convention (`ClassName` → `ClassNameTest`), return distributions for total/tested/untested counts

## 2. Prompt

- [x] 2.1 Create prompt `prompts/module_test_coverage_evaluation.md` — instruct LLM to evaluate test coverage gaps, untested classes, test-to-production ratio

## 3. Task Pipeline

- [x] 3.1 Add `ModuleTestCoverageEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_test_coverage_report`

## 4. Documentation

- [x] 4.1 Add test coverage evaluation section to `Documentation.md.hbs` — follow existing evaluation table pattern, use `testCoverageEvaluation` response property

## 5. Build and Verify

- [x] 5.1 Build with `mvn verify -DskipTests` — confirm compilation
- [x] 5.2 Run full test suite with `mvn verify` — confirm no regressions