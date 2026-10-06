# Tasks

## 1. Response format description

- [ ] 1.1 Expand an array's `items` schema in `json/JsonHelper.java` whenever it describes an object, with or without a `title`, and verify with a unit test that a nested property and its enum values appear in the description of a schema whose item objects carry no `title`
- [ ] 1.2 Name the properties an object level requires in the rendered description, and verify with a unit test that a required property which is not rendered would fail the test
- [ ] 1.3 Keep the enum rendering reachable at every level and add a nesting bound for the recursion, and verify with a unit test that a two-level nesting is rendered and a pathologically deep schema does not recurse without bound
- [ ] 1.4 Add a regression test over `analysis/software-architecture/results/ModuleBatchEvaluation.json` asserting the rendered description contains `urgency`, `criticality`, `NONE`, `LOW`, `MEDIUM` and `HIGH`
- [ ] 1.5 Confirm the affected prompts need no edit: the batch metric prompts describe the task, the format comes from the schema

## 2. Schema self-consistency

- [ ] 2.1 Remove `architecture` from the `required` list of `analysis/software-architecture/results/TechnologyStack.json`, and confirm by inspecting the stored prompt that the answer the model produced in the Maven run (reasoning, summary, technologies, derivations) is now conformant
- [ ] 2.2 Confirm no consumer of the domain reads an `architecture` property of the technology stack (documentation template, macros, documentation tasks)
- [ ] 2.3 Add a test that walks every `analysis/*/results/*.json` and fails when a name in `required` has no entry in `properties`, and verify it fails when the removed requirement is put back
- [ ] 2.4 Verify the schemas whose enum the model guessed (`ModuleBatchEvaluation.json`, `ArchitectureEvaluation.json`, `LicenseEvaluation.json`, `ModuleArchitecture.json`, `ModuleAnalysisReportsAll.json`, `ModuleSubdirectories.json`) are now described completely, and note any remaining schema whose item objects are opaque

## 3. Violation report and repair hint

- [ ] 3.1 Report the instance location, the evaluated property, the rejected value and the permitted values of a violation from `lc4j/ChatExecutor.java`, and verify with a unit test that an enum violation names the location and the rejected value
- [ ] 3.2 Report a missing required property with the location of the object that lacks it and the property name, and verify with a unit test
- [ ] 3.3 Make the report independent of the JVM default locale, and verify with a unit test that runs the validation under a German and an English default locale and compares the two reports
- [ ] 3.4 Bound the repair hint in violations and characters and state the total number of violations, and verify with a unit test that a 200-violation response produces a hint within the bound that names the total
- [ ] 3.5 Keep the complete violation list reachable for the diagnosis (chat log or a single engine log record), and verify that the bounded hint is the only bounded part
- [ ] 3.6 Re-check the interaction with `add-task-step-retries` if that change is applied first: the hint must remain part of the user message and follow the schema description

## 4. Project without an SBOM

- [ ] 4.1 Verify with a workspace whose `sbom.found` is false that the load task stores no SBOM data and reports the absence, and adjust `prompts/load_sbom.md` and its result schema if the current answer claims a load
- [ ] 4.2 Name the missing input in the tool result when an SBOM tool is called without a file to load, and verify with a unit test on the tool
- [ ] 4.3 Make `prompts/description_of_stack.md` and `results/TechnologyStack.json` accept an explicitly empty technology list with a stated reason, and verify with a unit test that a conformant empty answer validates against the schema
- [ ] 4.4 Verify the license evaluation accepts an empty action-item list with a stated reason, and adjust its prompt or schema if it does not
- [ ] 4.5 Verify with the `workspaces/maven` state that no sbom-dependent task is rejected because of the missing SBOM, and document the behaviour in `analysis/software-architecture/README.md`

## 5. Tool diagnostic levels

- [ ] 5.1 Log a tool call that names a path which does not exist as a tool result and at most one record per run below `ERROR` in `tools/file/FileIOTool.java`, and verify with a unit test that no stack trace is logged for a missing file while the tool result still names the condition
- [ ] 5.2 Count the files a Java module could not parse and report the count at most once per module below `ERROR` in `tools/java/JavaFileParser.java` and `tools/java/ClassFileParser.java`, keeping the per-file detail at `DEBUG`, and verify with a unit test that a tree with several unparsable files produces one record per module and the same module report as before
- [ ] 5.3 Verify a genuine tool failure (an exception of the tool itself) is still logged as an error

## 6. Verification

- [ ] 6.1 Run `mvn verify` and confirm the full suite, including the jar smoke test, passes
- [ ] 6.2 Re-run the analysis of the Maven workspace (copy, or after clearing the affected task states) and confirm that no batch metric evaluation is rejected, that `TechnologyStack` succeeds, and that the engine log contains no `ERROR` record for a missing `README` or for an unparsable source file
- [ ] 6.3 Confirm `state dump` shows no task failed for a reason that is not a real model failure
- [ ] 6.4 Run `openspec validate fix-response-contract --strict` and confirm the change validates
