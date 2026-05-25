## 1. Method Count per Class

- [x] 1.1 Implement `java_get_method_count_report` in `JavaTool.java` — iterate classes, count methods, return 3 `Distribution` objects
- [x] 1.2 Create prompt `prompts/module_method_count_evaluation.md`
- [x] 1.3 Add `ModuleMethodCountEvaluation` to `tasks.yaml`

## 2. Documentation Ratio

- [x] 2.1 Implement `java_get_documentation_ratio_report` in `JavaTool.java` — check `getDocumentation()` on classes and methods, return documented/undocumented counts
- [x] 2.2 Create prompt `prompts/module_documentation_ratio_evaluation.md`
- [x] 2.3 Add `ModuleDocumentationRatioEvaluation` to `tasks.yaml`

## 3. Data Class Detection

- [x] 3.1 Implement `java_get_data_class_report` in `JavaTool.java` — check field visibility + getter/setter pattern per class
- [x] 3.2 Create prompt `prompts/module_data_class_evaluation.md`
- [x] 3.3 Add `ModuleDataClassEvaluation` to `tasks.yaml`

## 4. Boolean Parameter Abuse

- [x] 4.1 Implement `java_get_boolean_parameter_report` in `JavaTool.java` — count `Z` in descriptor parameter portion per method
- [x] 4.2 Create prompt `prompts/module_boolean_parameter_evaluation.md`
- [x] 4.3 Add `ModuleBooleanParameterEvaluation` to `tasks.yaml`

## 5. Documentation Template

- [x] 5.1 Add 4 new sections to `Documentation.md.hbs` for method count, documentation ratio, data class detection, and boolean parameter evaluation — use response properties `methodCountEvaluation`, `documentationRatioEvaluation`, `dataClassEvaluation`, `booleanParameterEvaluation`

## 6. Build and Verify

- [x] 6.1 Build with `mvn verify -DskipTests` — confirm compilation
- [x] 6.2 Run full test suite with `mvn verify` — confirm no regressions