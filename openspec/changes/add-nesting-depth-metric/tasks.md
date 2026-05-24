## 1. Data Model

- [x] 1.1 Add `nestingDepth` (int, default 0) field with getter/setter to `Method.java`

## 2. Parser Population

- [x] 2.1 Implement `computeMaxDepth(Node)` recursive helper in `JavaFileParser.java` — traverses AST body, counts if/for/while/do-while/switch/try/for-each nesting depth
- [x] 2.2 Call `method.setNestingDepth(...)` after existing method fields are populated (after linesOfCode, before annotations)

## 3. New @Tool Method

- [x] 3.1 Implement `java_get_method_nesting_depth_report` in `JavaTool.java` — iterate module classes, collect nesting depth per method, return 3 `Distribution` objects (prod/test/gen)

## 4. Prompt

- [x] 4.1 Create prompt `prompts/module_nesting_depth_evaluation.md` — instruct LLM to call nesting depth tool + CC tool, evaluate deep nesting vs flat complexity patterns

## 5. Task Pipeline

- [x] 5.1 Add `ModuleNestingDepthEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_method_nesting_depth_report` and `java_get_cyclomatic_complexity_module_report`

## 6. Documentation

- [x] 6.1 Add nesting depth evaluation section to `Documentation.md.hbs` — follow existing cyclomatic complexity pattern, use `nestingDepthEvaluation` response property — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_method_nesting_depth_report` and `java_get_cyclomatic_complexity_module_report`

## 7. Build and Verify

- [x] 7.1 Build with `mvn verify -DskipTests` — confirm compilation
- [x] 7.2 Run full test suite with `mvn verify` — confirm no regressions