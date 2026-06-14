## ctx

Java analysis reports (`Java_*.json`) stored in workspace root directory via `JavaTool.java`. Three methods construct file paths using `context.getWorkingDirectory().resolve(reportId + ".json")`:

- `storeModuleReport()` — writes report to workspace root
- `getModuleReport()` — reads report from workspace root
- `reportFileExists()` — checks existence in workspace root

Existing workspaces:

- `workspaces/jabref/` — 9 `Java_*.json` files in root
- `workspaces/spring-petclinic/` — 1 `Java_*.json` file in root
- `workspaces/educational-platform/` — already has files in `java/` (lowercase) subdirectory

## Goals / Non-Goals

**Goals:**
- Store newly generated `Java_*.json` files in `Java/` subdirectory of workspace root
- Read existing reports from `Java/` subdirectory when reusing
- Migrate existing `Java_*.json` files from root to `Java/` for `jabref` and `spring-petclinic`
- Rename `educational-platform/java/` to `educational-platform/Java/` for consistency
- Update all existing `analysis.json` references to point to new locations
- Update tests to reflect new paths

**Non-Goals:**
- Not changing report naming convention (`Java_<moduleName>.json`)
- Not changing other CSV report directories
- Not adding migration logic to Java code — migration is one-time manual file move

## Decisions

**Decision 1: Hardcode `Java/` subdirectory path**
Store path as `Java/` constant in `JavaTool.java`. Simple approach — no config needed. If future need for configurable report directories arises, can refactor then.

**Decision 2: Manual migration, not code migration**
No automatic migration logic in Java code. Move files once at filesystem level. Old files in root for `jabref` and `spring-petclinic` move to `Java/`. `educational-platform/java/` renames to `Java/`. Update `analysis.json` references.

**Decision 3: Capital `J` in `Java/`**
Matches the report prefix `Java_`. Consistent casing.

## Changes

### Code changes (`JavaTool.java`)

Introduce constant:
```java
private static final String JAVA_REPORT_SUBDIRECTORY = "Java";
```

**`storeModuleReport()`** (line 146):
```java
// Old:
Path reportPath = context.getWorkingDirectory().resolve(reportId + ".json");
// New:
Path reportPath = context.getWorkingDirectory()
    .resolve(JAVA_REPORT_SUBDIRECTORY)
    .resolve(reportId + ".json");
```
Ensure subdirectory exists before write:
```java
Path reportDir = context.getWorkingDirectory().resolve(JAVA_REPORT_SUBDIRECTORY);
Files.createDirectories(reportDir);
Path reportPath = reportDir.resolve(reportId + ".json");
```

**`getModuleReport()`** (line 170):
```java
// Old:
Path reportPath = context.getWorkingDirectory().resolve(reportId + ".json");
// New:
Path reportPath = context.getWorkingDirectory()
    .resolve(JAVA_REPORT_SUBDIRECTORY)
    .resolve(reportId + ".json");
```

**`reportFileExists()`** (line 229):
```java
// Old:
return Files.exists(context.getWorkingDirectory().resolve(moduleNameToReportName(moduleName) + ".json"));
// New:
return Files.exists(context.getWorkingDirectory()
    .resolve(JAVA_REPORT_SUBDIRECTORY)
    .resolve(moduleNameToReportName(moduleName) + ".json"));
```

### Test changes (`JavaToolTest.java`)

- Update `tempDir.resolve("Java_core.json")` → `tempDir.resolve("Java/Java_core.json")`
- Ensure `Java/` subdirectory created in temp dir setup
- Update assertions referencing report paths

### Test changes (`DocumentationTemplateTest.java`)

- Check if any paths referenced — update if needed

### Workspace migration

1. `workspaces/jabref/`:
   - Create `workspaces/jabref/Java/`
   - Move `Java_*.json` → `Java/`
   - Update `analysis.json` report references

2. `workspaces/spring-petclinic/`:
   - Create `workspaces/spring-petclinic/Java/`
   - Move `Java_Spring_PetClinic_Sample_Application.json` → `Java/`
   - Update `analysis.json` report references

3. `workspaces/educational-platform/`:
   - Rename `java/` → `Java/`
   - Check `analysis.json` references (may already point to `java/`)

## Risks / Trade-offs

- **Backward compatibility**: Old workspaces without `Java/` directory will fail on next analysis run. Mitigation: provide clear migration instructions in release notes.
- **Analysis.json stale references**: If `analysis.json` stores absolute or relative paths to report files, those references will be broken. Need audit of how `analysis.json` references reports.
- **Educational-platform inconsistency**: Already has `java/` (lowercase). Renaming to `Java/` is trivial but `analysis.json` must be checked for lowercase references.