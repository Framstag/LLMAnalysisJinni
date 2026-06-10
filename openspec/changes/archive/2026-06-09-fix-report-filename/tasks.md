## 1. CsvReportWriter — Subdirectory support

- [x] 1.1 Add directory extraction: parse filename param to extract parent directory (e.g., `"NestingDepth/core-lib.csv"` → dir=`"NestingDepth"`)
- [x] 1.2 Add `Files.createDirectories(workingDir.resolve(dir))` before writing in all 6 methods (`writeCsv`, `writeMapCsv`, `writeMultiMapCsv`, `writeIntMapCsv`, `writeMultiIntMapCsv`, `writeListCsv`)
- [x] 1.3 Resolve full path as `workingDir.resolve(filename)` after directory creation
- [x] 1.4 Add try-catch for `IOException` on `createDirectories` — log error and return on failure

## 2. JavaTool — Module-name-aware filenames

All JavaTool report tasks below: change filename from `"<Report>.csv"` to `"<Report>/" + sanitizedModuleName + ".csv"`.

- [x] 2.1 Add helper method `sanitizeModuleName(String name)` — replace non-alphanumeric (except `-`, `.`, `_`) with `_`
- [x] 2.2 Update `getVisibilityDistributionReport`: `"VisibilityDistribution/" + sanitizeModuleName(moduleName) + ".csv"`
- [x] 2.3 Update `getInheritanceReport`: `"InheritanceDepth/" + ... `, `"InterfaceCount/" + ...`
- [x] 2.4 Update `getMethodComplexityReport`: `"MethodParamCount/" + ...`, `"MethodLinesOfCode/" + ...`
- [x] 2.5 Update `getMethodNestingDepthReport`: `"NestingDepth/" + ...`
- [x] 2.6 Update `getCyclomaticComplexityModuleReport`: `"CyclomaticComplexity/" + ...`
- [x] 2.7 Update `getFieldVisibilityReport`: `"FieldVisibility/" + ...`
- [x] 2.8 Update `getClassCohesionReport`: `"FieldCountPerClass/" + ...`, `"FieldToMethodRatio/" + ...`
- [x] 2.9 Update `getCouplingReport`: `"ClassCoupling/" + ...`, `"ModuleDependencies/" + ...`
- [x] 2.10 Update `getTestCoverageReport`: `"TestCoverage/" + ...`
- [x] 2.11 Update `getCircularDependencyReport`: `"CircularDeps/" + ...`
- [x] 2.12 Update `getMethodCountReport`: `"MethodCount/" + ...`
- [x] 2.13 Update `getDocumentationRatioReport`: `"DocumentationRatio/" + ...`
- [x] 2.14 Update `getDataClassReport`: `"DataClasses/" + ...`
- [x] 2.15 Update `getBooleanParameterReport`: `"BooleanParamFlags/" + ...`
- [x] 2.16 Update `getAnnotationReport`: `"AnnotationDensity/" + ...`
- [x] 2.17 Update `getPackageTangleReport`: `"PackageTangles/" + ...`
- [x] 2.18 Update `getImportDiversityReport`: `"ImportDiversity/" + ...`
- [x] 2.19 Update `writeDependencyMatrixCsv`: `"InterModuleDependencyMatrix/InterModuleDependencyMatrix.csv"` (cross-module method, no moduleName)
- [ ] 2.20 _(Skipped — `writeDependencyMatrixCsv` is cross-module, not per-module. Uses report-name fallback.)_

## 3. SBOMTool — Update CSV report calls

- [x] 3.1 Update `"AllLicenses.csv"` → `"AllLicenses/AllLicenses.csv"` in `writeLicencesReports`
- [x] 3.2 Update `"DependenciesAndLicenses.csv"` → `"DependenciesAndLicenses/DependenciesAndLicenses.csv"` in `writeLicencesReports`

## 4. Verify

- [x] 4.1 Build project with `mvn verify` — ensure no compilation or test failures
- [x] 4.2 Run a test analysis and confirm CSV files appear under subdirectories with module-name filenames