## 1. Data Model

- [x] 1.1 Create `Field.java` in `tools/java/` package with fields: `name` (String), `type` (String, FQN), `visibility` (MethodVisibility), `isStatic` (boolean), `isFinal` (boolean) — with Jackson annotations following `Method.java` pattern
- [x] 1.2 Add `List<Field> fields` field with `addField()` helper to `ClassManager.java`
- [x] 1.3 During report generation in `ModuleManager.java`, copy fields from `ClassManager` into `Clazz.java`

## 2. Parser Population

- [x] 2.1 Extract fields in `JavaFileParser.java` — iterate `type.getFields()`, resolve each variable declaration with type, visibility, static/final modifiers
- [x] 2.2 Extract fields in `ClassFileParser.java` — iterate `classModel.fields()`, extract name, type descriptor, access flags

## 3. New @Tool Methods

- [x] 3.1 Implement `java_get_field_visibility_report` — iterate module classes, collect field visibility + static/final counts per class, return 3 `Distribution` objects (prod/test/gen)
- [x] 3.2 Implement `java_get_class_cohesion_report` — iterate module classes, collect field count distribution, method-to-field ratio distribution, and field-access ratio distribution (methods that access fields vs total methods), return 3 groups of `Distribution` objects (prod/test/gen)

## 4. Prompts

- [x] 4.1 Create prompt `prompts/module_field_visibility_evaluation.md` — instruct LLM to evaluate field visibility distribution, detect public fields (non-constant), data classes, and god classes
- [x] 4.2 Create prompt `prompts/module_class_cohesion_evaluation.md` — instruct LLM to evaluate field count + method-to-field ratio + field-access ratio for cohesion assessment

## 5. Task Pipeline

- [x] 5.1 Add `ModuleFieldVisibilityEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_field_visibility_report`
- [x] 5.2 Add `ModuleClassCohesionEvaluation` task to `tasks.yaml` — depends on `module_analysis_reports`, loops over modules, whitelists `java_get_class_cohesion_report`

## 6. Documentation

- [x] 6.1 Add field visibility and class cohesion evaluation sections to `Documentation.md.hbs` — follow existing evaluation table pattern, use `fieldVisibilityEvaluation` and `classCohesionEvaluation` response properties

## 7. Build and Verify

- [x] 7.1 Build with `mvn verify -DskipTests` — confirm compilation
- [x] 7.2 Run full test suite with `mvn verify` — confirm no regressions