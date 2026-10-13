# Tasks

## 1. Model: drop the compilation unit level types

- [x] 1.1 Add `ParserHelper.isPackageOrModuleInfo(String qualifiedName)`, comparing the last dot-separated
      segment with `package-info` and `module-info`, with a javadoc that names the two files `javac` writes it
      for and why they are not classes of the module
- [x] 1.2 Extract the qualified name computation of `ClassFileParser.parseClassFile` into one private helper
      and call the parser skip between it and `getOrAddPackageByName`, so neither the package nor the build
      unit is created for such a file; log the skip at DEBUG with the file and the type, and return `false`
      with the javadoc of `parseClassFile` extended to "or declares no class of the module"
- [x] 1.3 Confirm no other code path turns a class file into a build unit (`ClassFileParser` is the only one;
      `JavaFileParser` returns before a build unit is created when a source file has no primary type)

## 2. Raw module report format

- [x] 2.1 Raise `Module.CURRENT_REPORT_FORMAT_VERSION` to 3 and name the exclusion in its javadoc
- [x] 2.2 Check every reader of the version: `JavaTool.generateAllModuleAnalysisReports` regenerates and states
      the reason, the diagram, split candidate, god class and class dependency consumers refuse an outdated
      report; `JavaToolTest` and `RawModuleReportFormatTest` pass unchanged, because they use the constant and
      version 1 as the outdated version

## 3. Tests

- [x] 3.1 Add a `parse` variant to `ClassFileParserReferenceTest` that writes each source under a given file
      name, so an annotated `package-info.java` and a `module-info.java` can be compiled beside a regular class
- [x] 3.2 Cover the package descriptor: `@Deprecated package demo;` beside `demo.Regular` yields no build unit
      whose name carries `package-info`, `demo.Regular` is present, and `parseClassFile` reports the descriptor
      as a file that contributed no type
- [x] 3.3 Cover the module descriptor: `module demo.mod {}` yields no build unit for `module-info.class` and no
      package entry at all
- [x] 3.4 Cover the unchanged neighbour case from the existing fixtures, so the skip cannot silence a regular
      class file (`ClassFileParserReferenceTest` runs 10 tests, all green)

## 4. Verification on real data

- [x] 4.1 Run `mvn verify` and confirm the unit tests pass (390 unit tests, 6 smoke tests, BUILD SUCCESS)
- [x] 4.2 Regenerate the real reports through the analysis pipeline instead of
      `-Dllmaj.regenerateWorkspaces=true`: drop `CollectJavaModuleReportsAll` (and, in Maven,
      `ClassDependencyGraphEvaluationAll`) and rerun `analyse`, because a skipped task never calls the tool that
      regenerates a report. All 87 Maven and all 9 jabref reports now record version 3
- [x] 4.3 Assert on the regenerated reports that no build unit name carries `package-info` and none carries
      `module-info`: 0 of the 87 Maven reports match, 0 of the 9 jabref reports match, and the metric CSV
      reports of the Maven workspace hold no such row either
- [x] 4.4 Run `mvn test -Dtest=RealModuleDiagramTest`: it measured `Apache_Maven.impl.maven-core` and `jablib`
      and passed, where it skipped 2 tests before the regeneration; the Maven analysis state holds a
      `GENERATED` diagram status for all 84 Java modules and none for the 3 non-Java ones
- [x] 4.5 The Maven workspace holds no inheritance, visibility or import diversity row naming a
      `*package-info`; `document` rewrote `Documentation.adoc` from 15 "No diagram was generated" reasons to
      273 PlantUML blocks and none
- [x] 4.6 Rerun every evaluation that reads the module reports, because a report that was regenerated does not
      make a stored evaluation that was derived from the old one correct: drop `TestCoverageEvaluationAll` and
      the other 18 `*EvaluationAll` tasks whose tool whitelist reads the reports, rerun `analyse`, then
      `document`. All 18 steps succeeded, all 38 Maven tasks report successful, and
      `Apache_Maven.api.maven-api-annotations` reads 11 production classes instead of 12, which matches the 11
      class records of its regenerated report
- [x] 4.7 Every remaining `package-info` hit in `analysis.json` (46) sits under `modules`, where the per-module
      narrative names the `package-info.java` source file of a package, and under
      `build_analysis[].evaluations`, where it describes a `maven-compiler-plugin` exclusion of
      `'**/package-info.java'`. Both are gathered from the file system and are true statements about the source
      tree of those modules, not report derived counts, so they stay as they are
- [ ] 4.8 Left open, outside the Maven workspace: the jabref diagram task fails on a prompt of 528,267 tokens
      against the 131,072 context of its `gpt-oss:20b`, which no report format can fit; four `ImportDiversity`
      CSV rows of the jabref workspace keep their `.module-info` row until that task runs again
