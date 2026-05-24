## 1. Data Model Changes

- [x] 1.1 Create `MethodVisibility.java` enum with values `PUBLIC`, `PROTECTED`, `PACKAGE_PRIVATE`, `PRIVATE` in `tools/java/` package
- [x] 1.2 Add `visibility` (MethodVisibility, nullable), `isStatic` (boolean), `isFinal` (boolean), `parameterCount` (int), `linesOfCode` (Integer, nullable) fields to `Method.java`
- [x] 1.3 Add `superClass` (String, nullable, fully-qualified) and `interfaces` (List<String>, fully-qualified) fields to `Clazz.java`
- [x] 1.4 Update `Method.java` and `Clazz.java` Jackson annotations for JSON serialization of new fields

## 2. Parser Population

- [x] 2.1 Populate new `Method.java` fields in `JavaFileParser.java` — extract visibility, static/final modifiers, parameter count, and body statement count from JavaParser AST
- [x] 2.2 Populate new `Clazz.java` fields in `JavaFileParser.java` — extract superclass name and interface names from `ClassOrInterfaceDeclaration`
- [x] 2.3 Populate `superClass` and `interfaces` in `ClassFileParser.java` — data already parsed but currently discarded in favor of debug logging

## 3. New @Tool Methods in JavaTool.java

- [x] 3.1 Implement `java_get_visibility_distribution_report` — iterate module classes, collect visibility + static/final counts per method, return 3 `Distribution` objects (prod/test/gen)
- [x] 3.2 Implement `java_get_inheritance_report` — iterate module classes, compute inheritance depth by traversing superClass chain, count interfaces per class, return 3 pairs of `Distribution` objects
- [x] 3.3 Implement `java_get_method_complexity_report` — iterate module classes, collect parameterCount and linesOfCode per method, return 3 pairs of `Distribution` objects (param count + LoC)

## 4. Analysis Prompts and Schemas

- [x] 4.1 Create prompt `prompts/module_visibility_evaluation.md` — instruct LLM to evaluate visibility distribution with encapsulation/anemic-domain/static-overuse heuristics
- [x] 4.2 Create prompt `prompts/module_inheritance_evaluation.md` — instruct LLM to evaluate inheritance depth, interface count, hierarchy patterns
- [x] 4.3 Create prompt `prompts/module_method_complexity_evaluation.md` — instruct LLM to evaluate parameter count + LoC distributions, cross-reference with cyclomatic complexity
- [x] 4.4 Create or reuse `results/ArchitectureEvaluation.json` schema for all 3 new tasks (existing schema fits — same format as `ModuleCyclomaticComplexityEvaluation`)

## 5. Task Pipeline Registration

- [x] 5.1 Add `ModuleVisibilityEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_visibility_distribution_report`
- [x] 5.2 Add `ModuleInheritanceEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_inheritance_report`
- [x] 5.3 Add `ModuleMethodComplexityEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_method_complexity_report`

## 6. Build and Verify

- [x] 6.1 Build project with `mvn verify -DskipTests` — confirm compilation
- [x] 6.2 Run full test suite with `mvn verify` — confirm no regressions
- [x] 6.3 Run analysis on a test project — verify new tools produce expected reports and LLM tasks complete successfully