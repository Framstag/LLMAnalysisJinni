# Tasks

## 1. The class record and the format version

- [x] 1.1 Record the source file of a class in the class record of the raw Java module report and raise the format
      version of the report to 5 (version 4 added the interface flag of a class); verify with
      `RawModuleReportFormatTest` that the written report holds the path and that the version is 5
- [x] 1.2 Let the report task regenerate a report of an older version and state the recorded version, the current
      version and that the report was regenerated; verify with `RegenerateWorkspaceReportsTest` that a report of the
      old version is replaced
- [x] 1.3 Confirm that the existing metric tools read a report of the new format without a change of their result;
      verify with the metric tests of the engine and with `ExistingMetricReportCompatibilityTest`

## 2. The hotspot count

- [x] 2.1 Add the tool that joins the counted history of the project with the counted complexity of the classes and
      returns per class the change count of its file, the added and removed lines, the counted complexity and the
      method count; verify with a unit test over a report and a counted history that the join holds the figures of
      both sides
- [x] 2.2 Rank the classes by change frequency and complexity together, report both figures beside every rank and
      leave out a class whose file never changed; verify with a unit test that the order of two classes follows the two
      figures and that a never changed class holds no rank
- [x] 2.3 Report the hotspots as absent with the reason when the counted history is absent; verify with a unit test
      that the result names the absence and holds no ranking
- [x] 2.4 Write the counted hotspots as a project level CSV report with one row per class; verify with a unit test
      that the file holds the change count, the complexity and the rank beside each class
- [x] 2.5 Register the tool in the tool factory; verify that the tool appears in the tool list of the engine and that
      its parameters are named

## 3. The evaluation

- [x] 3.1 Write the response schema of the hotspot evaluation and the prompt that asks for the evaluation of the
      counted hotspots and that states the limits of the join, among them the counted window of the history and the
      change count of the file of a class; verify that the schema parses, that it declares every property it requires
      and that a rendered sample answer validates against it
- [x] 3.2 Add the task that runs the hotspot evaluation to `tasks.yaml` with its response property, its dependencies
      and a tool whitelist that holds only the tools the prompt calls; verify that the task definition validation
      passes and that the task is active
- [x] 3.3 Make the evaluation report the absence of the history instead of an empty evaluation; verify with a test
      that an evaluation of a project without a history names the absence with its reason

## 4. Report and documentation

- [x] 4.1 Render the hotspots in `documentation/Documentation.adoc.hbs` as a table of the ranked classes with their
      two figures and render the absence of the history with its reason; verify with `DocumentationTemplateTest` that
      the section is rendered and that it carries its pointer line
- [x] 4.2 Add the entry for the hotspot analysis to `documentation/AnalysisReference.adoc.hbs` with the parts every
      entry carries and the hotspot specifics, among them the two figures the hotspot joins, that neither figure alone
      makes a hotspot and the bound of the counted window; verify with `AnalysisReferenceTest` that every entry
      carries all its parts and that the documented analyses have exactly one entry each
- [x] 4.3 Add the row for the hotspot analysis to the task table of the README of the analysis; verify with
      `SoftwareArchitectureTaskConfigTest` that the task table holds the new row

## 5. Integration checks

- [x] 5.1 Run `mvn verify` and confirm the build passes, that the report format version is the new one and that no
      existing metric changed its result; verify with the metric tests of the engine
- [x] 5.2 Run the specification validation of the project and confirm that the change validates; verify with
      `openspec validate add-hotspot-analysis --strict`

## 6. Review follow-up

- [x] 6.1 Name the build units of a module that lie outside the namespace of the module in the result of the
      hotspot tool, with the reason of the exclusion, and keep the count of the classes of the module; verify
      with a unit test that the result names the excluded build unit with its reason and that the class of that
      build unit holds no row

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
