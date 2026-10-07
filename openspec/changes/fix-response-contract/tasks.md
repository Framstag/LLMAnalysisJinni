# Tasks

## 1. Response format description

- [x] 1.1 Expand an array's `items` schema in `json/JsonHelper.java` whenever it describes an object, with or without a `title`, and verify with a unit test that a nested property and its enum values appear in the description of a schema whose item objects carry no `title`
- [x] 1.2 Name the properties an object level requires in the rendered description, and verify with a unit test that a required property which is not rendered would fail the test
- [x] 1.3 Keep the enum rendering reachable at every level and add a nesting bound for the recursion, and verify with a unit test that a two-level nesting is rendered and a pathologically deep schema does not recurse without bound
- [x] 1.4 Add a regression test over `analysis/software-architecture/results/ModuleBatchEvaluation.json` asserting the rendered description contains `urgency`, `criticality`, `NONE`, `LOW`, `MEDIUM` and `HIGH`
- [x] 1.5 Confirm the affected prompts need no edit: the batch metric prompts describe the task, the format comes from the schema — *checked: the only prompt naming an enum value is `nesting_depth_evaluation_all.md`, and it names `NONE`/`NONE` the way the schema spells it*

## 2. Schema self-consistency

- [x] 2.1 Remove `architecture` from the `required` list of `analysis/software-architecture/results/TechnologyStack.json`, and confirm by inspecting the stored prompt that the answer the model produced in the Maven run (reasoning, summary, technologies, derivations) is now conformant
- [x] 2.2 Confirm no consumer of the domain reads an `architecture` property of the technology stack (documentation template, macros, documentation tasks) — *checked: `Documentation.adoc.hbs` renders `technologyStack.reasoning/summary/technologies/derivations` only; the `architecture` it renders is the module architecture task's response property*
- [x] 2.3 Add a test that walks every `analysis/*/results/*.json` and fails when a name in `required` has no entry in `properties`, and verify it fails when the removed requirement is put back
- [x] 2.4 Verify the schemas whose enum the model guessed (`ModuleBatchEvaluation.json`, `ArchitectureEvaluation.json`, `LicenseEvaluation.json`, `ModuleArchitecture.json`, `ModuleAnalysisReportsAll.json`, `ModuleSubdirectories.json`) are now described completely, and note any remaining schema whose item objects are opaque — *scanned all 18 schemas of the domain: no array of objects stays opaque and no required property is undeclared*

## 3. Violation report and repair hint

- [x] 3.1 Report the instance location, the evaluated property, the rejected value and the permitted values of a violation from `lc4j/ChatExecutor.java`, and verify with a unit test that an enum violation names the location and the rejected value
- [x] 3.2 Report a missing required property with the location of the object that lacks it and the property name, and verify with a unit test
- [x] 3.3 Make the report independent of the JVM default locale, and verify with a unit test that runs the validation under a German and an English default locale and compares the two reports
- [x] 3.4 Bound the repair hint in violations and characters and state the total number of violations, and verify with a unit test that a 200-violation response produces a hint within the bound that names the total
- [x] 3.5 Keep the complete violation list reachable for the diagnosis (chat log or a single engine log record), and verify that the bounded hint is the only bounded part
- [x] 3.6 Re-check the interaction with `add-task-step-retries` if that change is applied first: the hint must remain part of the user message and follow the schema description — *that change is merged into this branch; `ChatExecutorOutcomeTest.repairHintFollowsTheSchemaDescription` covers it and stays green*

## 4. Project without an SBOM

- [x] 4.1 Verify with a workspace whose `sbom.found` is false that the load task stores no SBOM data and reports the absence, and adjust `prompts/load_sbom.md` and its result schema if the current answer claims a load — *the Maven workspace stores `{"result":"ERROR"}`; the prompt now states the no-SBOM case, and `results/Result.json` gained an optional `reason` so the absence can be named*
- [x] 4.2 Name the missing input in the tool result when an SBOM tool is called without a file to load, and verify with a unit test on the tool
- [x] 4.3 Make `prompts/description_of_stack.md` and `results/TechnologyStack.json` accept an explicitly empty technology list with a stated reason, and verify with a unit test that a conformant empty answer validates against the schema
- [x] 4.4 Verify the license evaluation accepts an empty action-item list with a stated reason, and adjust its prompt or schema if it does not — *the prompt already requires an empty list plus a reason, and the Maven run stored exactly that (0 licenses, reason naming the absent SBOM); the schema accepts it, covered by test*
- [x] 4.5 Verify with the `workspaces/maven` state that no sbom-dependent task is rejected because of the missing SBOM, and document the behaviour in `analysis/software-architecture/README.md` — *`LoadSBOM`, `DependencyList` and `LicenseEvaluation` are successful in `state dump`; the only failed task is `TechnologyStack`, and its rejection was the undeclared required property, not the missing SBOM*

## 5. Tool diagnostic levels

- [x] 5.1 Log a tool call that names a path which does not exist as a tool result and at most one record per run below `ERROR` in `tools/file/FileIOTool.java`, and verify with a unit test that no stack trace is logged for a missing file while the tool result still names the condition
- [x] 5.2 Count the files a Java module could not parse and report the count at most once per module below `ERROR` in `tools/java/JavaFileParser.java` and `tools/java/ClassFileParser.java`, keeping the per-file detail at `DEBUG`, and verify with a unit test that a tree with several unparsable files produces one record per module and the same module report as before
- [x] 5.3 Verify a genuine tool failure (an exception of the tool itself) is still logged as an error

## 6. Verification

- [x] 6.1 Run `mvn verify` and confirm the full suite, including the jar smoke test, passes
- [x] 6.2 Re-run the analysis of the Maven workspace (copy, or after clearing the affected task states) and confirm that no batch metric evaluation is rejected, that `TechnologyStack` succeeds, and that the engine log contains no `ERROR` record for a missing `README` or for an unparsable source file — *closed on test coverage, as agreed: no model endpoint is reachable from this environment (`http_000` on `localhost:11434`), so no run could be performed. Each claim is covered where its cause was fixed: the batch metric description by `JsonHelperSchemaDescriptionTest.testBatchMetricSchemaIsDescribedCompletely` and `testEnumeratedSchemasOfTheDomainAreDescribedWithTheirValues`, the technology stack answer of the run by `AnalysisResponseSchemaTest.testTheTechnologyStackSchemaAcceptsTheAnswerOfTheRun`, and the two log conditions by `FileIOToolTest` and `JavaToolTest.unparsableFilesAreReportedPerModule`. A manual re-run remains a follow-up for a machine with a model.*
- [x] 6.3 Confirm `state dump` shows no task failed for a reason that is not a real model failure — *`state dump workspaces/maven` reports `TechnologyStack` as the only failed task, which was unsatisfiable before this change; six tasks are still pending from the stopped run*
- [x] 6.4 Run `openspec validate fix-response-contract --strict` and confirm the change validates
