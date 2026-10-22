# Tasks

## 1. Verify the corrected statements of the import diversity capability

- [x] 1.1 Verify that the report returns three distributions and not three pairs, and that every entry of a distribution is marked as internal or external, by reading `JavaTool.getImportDiversityReport` in `src/main/java/com/framstag/llmaj/tools/java/JavaTool.java`; verified: the method returns `List.of(prodDist, testDist, genDist)` and labels every entry with `isInternal(...) ? " (internal)" : " (external)"`
- [x] 1.2 Verify that the package namespace of a module is the package of one of its classes, by reading `JavaTool.determineProjectNamespace`; verified: the method returns the package of the first class whose qualified name carries a dot, and returns `null` when no class does
- [x] 1.3 Verify that a module without a package namespace marks every import source as external, by reading `JavaTool.isInternal`; verified: the method returns `false` when the package namespace is `null`
- [x] 1.4 Verify that static imports and imports of `java.lang` are not counted as import sources, by reading the import loop of `JavaTool.getImportDiversityReport`; verified: the loop skips every import that starts with `static ` or with `java.lang`
- [x] 1.5 Verify that an import source prefix is internal when it is the package namespace of the module or a prefix of it, by reading `JavaTool.isInternal`; verified: the method returns `true` when the prefix equals the namespace or the namespace starts with the prefix

## 2. Verify the corrected statements of the nesting depth and of the inter-module evaluation

- [x] 2.1 Verify that a control structure at the top level of a method already has a nesting depth of 1, by reading `JavaFileParser.computeMaxDepth`; verified: the method counts a control structure child as `1 + computeMaxDepth(child)`, so a single top level control structure yields 1
- [x] 2.2 Verify that a control structure whose body is a single statement without braces adds no level of depth, by reading `JavaFileParser.computeMaxDepth`; verified: only the control structure statements themselves add a level, and a plain statement is not one of them
- [x] 2.3 Verify that a method parsed from a class file has a nesting depth of 0, by reading `JavaFileParser` and `ClassFileParser`; verified: the nesting depth is set only for methods and constructors of a parsed source file, and `Method` initialises the depth to 0
- [x] 2.4 Verify that the evaluation treats an instability below 30 as stable, 30 to 70 as balanced and above 70 as unstable, by reading `analysis/software-architecture/prompts/inter_module_dependency_evaluation.md`; verified: the prompt states `Instability > 70`, `Instability < 30` and `Instability 30-70` in its interpretation list
- [x] 2.5 Verify that the evaluation considers the number of imports between a pair of modules, by reading the same prompt; verified: the prompt names the number of imports between module pairs as a criterion of tight coupling

## 3. Verify the scenarios the change adds to the metric capabilities

- [x] 3.1 Verify the two scenarios added to `annotation-density`, by reading `JavaTool.getAnnotationReport` and the annotation collection of `JavaFileParser`; verified: the report counts the annotations of the parsed class and method nodes, so a built-in annotation is counted and an annotation that only appears in the import graph is not
- [x] 3.2 Verify the four scenarios added to `import-diversity`, by reading `JavaTool.getImportDiversityReport`, `JavaTool.isInternal` and `JavaTool.determineProjectNamespace`; verified: the entries of a distribution are marked internal when the prefix is the package namespace of the module or a prefix of it, external otherwise, and a module without a package namespace marks every entry as external
- [x] 3.3 Verify the four scenarios added to `inter-module-dependency-analysis`, by reading `JavaTool.findTargetModule` and the import loop of `JavaTool.getInterModuleDependencyReport`; verified: the exact class match wins first, a wildcard import is matched by the package it names, the longest matching namespace wins, the JDK and static imports are skipped, and the instability is computed as `(ce * 100) / (ce + ca)`
- [x] 3.4 Verify the three scenarios added to `package-tangle-detection`, by reading `JavaTool.getPackageTangleReport`; verified: the package of an import is the import string without its last segment, only the packages of the module itself are linked, and the static and `java.lang` imports are skipped
- [x] 3.5 Verify the scenario added to `java-method-nesting-analysis` for a control structure at the top level, by reading `JavaFileParser.computeMaxDepth`; verified: the depth of a method with one top level control structure is 1

## 4. Verify the scenarios the change adds to the runtime capabilities

- [x] 4.1 Verify the scenario added to `live-progress-display` for the fields of the engine log record, by reading the log pattern of `src/main/java/com/framstag/llmaj/logging/EngineLogRouting.java`; verified: the pattern states the level, the logger and the message, and the task identifier through the MDC
- [x] 4.2 Verify that the clause the change drops from the engine log requirement, that a run with nothing to execute starts no TUI, is stated by another requirement of the same capability, by reading the requirement `A run with nothing to execute is reported`; verified: that requirement states that the system shall not start the live terminal UI
- [x] 4.3 Verify that the clause the change drops from the engine log requirement, that the components write their diagnostics through the logging path, is stated by a scenario of the same requirement, by reading the scenario `Tool diagnostics cannot bypass the routing`; verified: the scenario states that no component writes a diagnostic directly to the terminal
- [x] 4.4 Verify the four scenarios added to `llm-interaction-logger`, by reading the resolution of the execution trace in the configuration and the console appender of the logger; verified: the console output of the chat messages follows the effective execution trace, the flag wins over the workspace configuration, and an inactive trace leaves the chat messages off the console
- [x] 4.5 Verify that the removed requirement of `llm-interaction-logger` leaves no behaviour of the console unstated, by comparing its scenarios with the scenarios of the new requirement and with those of the requirement `--execution-trace flag disables TUI`; verified: every behaviour of the removed requirement is stated by one of the two
- [x] 4.6 Verify the two scenarios added to `llm-response-retry`, by reading the construction of the repair hint in `src/main/java/com/framstag/llmaj/lc4j/ChatExecutor.java`; verified: the hint follows the schema description, and a payload failure puts the message of the parser and a bounded excerpt of the rejected payload into the hint

## 5. Verify the clauses the change drops because a scenario states them

- [x] 5.1 Verify that the dropped clause of the raw module report requirement, that a report of an older format version is not reused, is stated by the scenarios `Report written before the compilation unit level types were excluded is regenerated` and `Outdated report is refused instead of analysed`; verified: both scenarios state what happens to a report of an older version
- [x] 5.2 Verify that the dropped clause of the raw module report requirement, that the metric tools keep their inputs for reports written before the record existed, is stated by the scenario `Report written before the weighted record stays readable`; verified: the scenario states that a report without the record returns the result of before the change

## 6. Apply the change and check the result

- [x] 6.1 Run `openspec validate split-the-long-requirements --strict` and confirm the change validates; verified: the command reports the change as valid
- [x] 6.2 Run `openspec archive split-the-long-requirements --yes` and confirm the archive applies the deltas; verified: the archive names the ten capabilities, reports the modified, added and removed requirements, and the retired requirement of the logger capability is absent afterwards
- [x] 6.3 Run `openspec validate --all --strict` and confirm that every specification of the project validates; verified: the command reports all fifty-four specifications and the change in flight as valid, and no specification reports a requirement text longer than 500 characters. The first run after the archive reported two specifications as failed, `batch-java-metric-evaluation` and `batch-java-report-collection`, because both still carried the placeholder Purpose that the archive of the change `batch-java-module-evaluation` had written into them. Their Purpose was replaced directly in the main specifications, because the Purpose of an existing capability cannot be reached by a delta. The second run reports 55 passed, 0 failed.
- [x] 6.4 Confirm that no scenario was lost by the archive, by comparing the scenarios of the changed capabilities before and after the archive; verified: every scenario of the twelve long requirements is present afterwards, and the scenarios that the change adds are present as well

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied. — done: archived as `2026-10-22-split-the-long-requirements`
- Verify the archived result: the twelve long requirements are under the length the strict validation allows, the three corrected capabilities describe the code, and the execution trace rule is stated once per concern. — done: the archive reported `+ 5, ~ 11, - 1, → 0`, the comparison of the scenarios before and after the archive reports none lost, and `openspec validate --all --strict` reports 55 passed, 0 failed
- The change that puts the strict validation into the continuous integration run starts after this change is archived, because the validation fails on the specifications as long as this change is not applied. — the change `gate-the-specifications-in-ci` is written and waits for this change to be archived
