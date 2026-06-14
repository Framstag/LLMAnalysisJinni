## 1. Core code changes

- [x] 1.1 Add `JAVA_REPORT_SUBDIRECTORY` constant to `JavaTool.java`
- [x] 1.2 Update `storeModuleReport()` to write to `Java/` subdirectory with `createDirectories`
- [x] 1.3 Update `getModuleReport()` to read from `Java/` subdirectory
- [x] 1.4 Update `reportFileExists()` to check inside `Java/` subdirectory

## 2. Test updates

- [x] 2.1 Update `JavaToolTest.java` file path assertions to use `Java/` subdirectory
- [x] 2.2 Update `JavaToolTest.java` report descriptor assertions
- [x] 2.3 Verify `DocumentationTemplateTest.java` — update if needed

## 3. Build and verify

- [x] 3.1 Run `mvn verify` to confirm tests pass

## 4. Workspace migration

- [x] 4.1 Move `workspaces/jabref/Java_*.json` to `workspaces/jabref/Java/`
- [x] 4.2 Move `workspaces/spring-petclinic/Java_Spring_PetClinic_Sample_Application.json` to `workspaces/spring-petclinic/Java/`
- [x] 4.3 Rename `workspaces/educational-platform/java/` to `workspaces/educational-platform/Java/`