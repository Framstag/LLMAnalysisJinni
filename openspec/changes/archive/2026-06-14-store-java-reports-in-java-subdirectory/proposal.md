## What Changes

Java scan reports (`Java_*.json`) currently stored in workspace root directory. Change stores them in `Java/` subdirectory within workspace.

Changes:
- `JavaTool.java` — `storeModuleReport()` writes to `Java/<reportId>.json` instead of `<reportId>.json`
- `JavaTool.java` — `getModuleReport()` reads from `Java/<reportId>.json` instead of `<reportId>.json`
- `JavaTool.java` — `reportFileExists()` checks `Java/<reportId>.json` instead of `<reportId>.json`
- `JavaToolTest.java` — update assertions and file paths to match new subdirectory
- `DocumentationTemplateTest.java` — update any references if needed
- Migrate existing `Java_*.json` files in `workspaces/jabref/` and `workspaces/spring-petclinic/` into `workspaces/jabref/Java/` and `workspaces/spring-petclinic/Java/`
- `workspaces/educational-platform/java/` already has files in subdirectory (lowercase) — rename to `Java/` to match new convention

## Capabilities

### New Capabilities

- `java-reports-subdirectory`: Store Java analysis report files in `Java/` subdirectory of workspace root instead of workspace root directly

### Modified Capabilities

- (none — no requirement changes at spec level)

## Impact

- **`src/main/java/com/framstag/llmaj/tools/java/JavaTool.java`**: 3 methods need path changes — `storeModuleReport()` (line 146), `getModuleReport()` (line 170), `reportFileExists()` (line 229)
- **`src/test/java/com/framstag/llmaj/tools/java/JavaToolTest.java`**: Update file path assertions and report descriptor checks
- **`src/test/java/com/framstag/llmaj/documentation/DocumentationTemplateTest.java`**: May need path updates if referencing file location
- **Workspaces `jabref/`**: Move 9 `Java_*.json` files into `Java/` subdirectory
- **Workspaces `spring-petclinic/`**: Move `Java_Spring_PetClinic_Sample_Application.json` into `Java/` subdirectory
- **Workspaces `educational-platform/`**: Rename `java/` directory to `Java/` (capitalize) — files already present