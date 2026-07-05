## 1. Prompt Updates

- [x] 1.1 Add object format requirement to `annotation_evaluation_all.md`
- [x] 1.2 Add object format requirement to `boolean_parameter_evaluation_all.md`
- [x] 1.3 Add object format requirement to `circular_dependency_evaluation_all.md`
- [x] 1.4 Add object format requirement to `class_cohesion_evaluation_all.md`
- [x] 1.5 Add object format requirement to `coupling_evaluation_all.md`
- [x] 1.6 Add object format requirement to `cyclomatic_complexity_evaluation_all.md`
- [x] 1.7 Add object format requirement to `data_class_evaluation_all.md`
- [x] 1.8 Add object format requirement to `documentation_ratio_evaluation_all.md`
- [x] 1.9 Add object format requirement to `field_visibility_evaluation_all.md`
- [x] 1.10 Add object format requirement to `import_diversity_evaluation_all.md`
- [x] 1.11 Add object format requirement to `inheritance_evaluation_all.md`
- [x] 1.12 Add object format requirement to `method_complexity_evaluation_all.md`
- [x] 1.13 Add object format requirement to `method_count_evaluation_all.md`
- [x] 1.14 Add object format requirement to `nesting_depth_evaluation_all.md`
- [x] 1.15 Add object format requirement to `package_tangle_evaluation_all.md`
- [x] 1.16 Add object format requirement to `test_coverage_evaluation_all.md`
- [x] 1.17 Add object format requirement to `visibility_evaluation_all.md`

## 2. Template Simplification

- [x] 2.1 Remove `{{#if aspect}}` fallback branch from all evaluation sections in `Documentation.md.hbs`
- [x] 2.2 Remove `{{#if finding}}` fallback branch from all evaluation sections in `Documentation.md.hbs`
- [x] 2.3 Verify template renders single table row per evaluation item with no conditionals

## 3. Verification

- [x] 3.1 Build project with `mvn verify -DskipTests`
- [x] 3.2 Regenerate documentation with `mvn exec:java -Dexec.mainClass="com.framstag.llmaj.Main" -Dexec.args="document workspaces/llmanalysisjinni"`
- [x] 3.3 Verify all evaluation table columns render correctly in generated `Documentation.md`
- [x] 3.4 Run existing tests with `mvn verify`

## 4. Cleanup

- [x] 4.1 Remove temporary script files (`_tmp_*.py`)
- [x] 4.2 Archive this change with `openspec archive fix-documentation`
