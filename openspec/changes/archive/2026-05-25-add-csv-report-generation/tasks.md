## 1. Visibility Distribution CSV

- [x] 1.1 Add CSV writing to `getVisibilityDistributionReport` — columns: Class, Visibility, isStatic, isFinal — sorted by visibility

## 2. Inheritance Report CSV

- [x] 2.1 Add CSV writing to `getInheritanceReport` — columns: Class, Depth, InterfaceCount — sorted by depth descending

## 3. Method Complexity CSV

- [x] 3.1 Add CSV writing to `getMethodComplexityReport` — columns: Class, Method, ParameterCount, LinesOfCode

## 4. Nesting Depth CSV

- [x] 4.1 Add CSV writing to `getMethodNestingDepthReport` — columns: Class, Method, NestingDepth — sorted by depth descending

## 5. Field Visibility CSV

- [x] 5.1 Add CSV writing to `getFieldVisibilityReport` — columns: Class, Field, Visibility, isStatic, isFinal

## 6. Class Cohesion CSV

- [x] 6.1 Add CSV writing to `getClassCohesionReport` — columns: Class, FieldCount, MethodCount, Ratio

## 7. Coupling CSV

- [x] 7.1 Add CSV writing to `getCouplingReport` — columns: Class, Coupling, ModuleDependency — sorted by coupling descending

## 8. Test Coverage CSV

- [x] 8.1 Add CSV writing to `getTestCoverageReport` — columns: Class, HasTest — untested classes first

## 9. Circular Dependency CSV

- [x] 9.1 Add CSV writing to `getCircularDependencyReport` — columns: CycleNumber, ClassName

## 10. Method Count CSV

- [x] 10.1 Add CSV writing to `getMethodCountReport` — columns: Class, MethodCount — sorted by count descending

## 11. Documentation Ratio CSV

- [x] 11.1 Add CSV writing to `getDocumentationRatioReport` — columns: Class, HasDoc, MethodCount, DocumentedMethods

## 12. Data Class CSV

- [x] 12.1 Add CSV writing to `getDataClassReport` — columns: ClassName

## 13. Boolean Parameter CSV

- [x] 13.1 Add CSV writing to `getBooleanParameterReport` — columns: Class, Method, BooleanParamCount

## 14. Cyclomatic Complexity CSV

- [x] 14.1 Add CSV writing to `getCyclomaticComplexityModuleReport` — columns: Class, Method, CC — sorted by CC descending

## 15. Build and Verify

- [x] 15.1 Build with `mvn verify -DskipTests` — confirm compilation
- [x] 15.2 Run full test suite with `mvn verify` — confirm no regressions