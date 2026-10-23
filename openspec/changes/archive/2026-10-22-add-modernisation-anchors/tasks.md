# Tasks

## 1. Marker facts

- [x] 1.1 Write `analysis/software-architecture/facts/modernisation_markers.md` with the legacy namespaces that mark a
      legacy dependency, the version ranges that mark a dependency at the end of its life, the annotations that mark
      an entry point, the interfaces that mark a service provider interface, and the catalogue of modernisation
      patterns; verify that the file holds all five parts and that each pattern of the catalogue carries a name, a
      short description and what it would make the wrong choice
- [x] 1.2 Name in the marker facts that the legacy namespaces and the version ranges are a snapshot of a date, and
      that the catalogue is the only source of the patterns an anchor may be matched against; verify that both
      statements are in the file

## 2. Seam detection

- [x] 2.1 Add the seam tool that reads the raw module reports of the project and returns, per interface, the
      declaring module, the implementing classes with their modules, the referencing classes of the declaring module,
      the importing modules and whether the interface is a port candidate; verify with a unit test over two module
      reports that the counts, the port candidate decision and the skipped module are correct
- [x] 2.2 Order the seams so that the interfaces with more implementors and more consumers come first, and report a
      module without a report as skipped; verify with a unit test that the order of two calls is the same and that the
      skipped module is named
- [x] 2.3 Write the seam evidence as the project level CSV report `Seams/` with one row per interface; verify with a
      unit test that the file is written with one row per interface and the fields of the spec
- [x] 2.4 Register the seam tool in the tool factory under a name of the `java_` family; verify that the tool
      appears in the tool list of the engine and that its parameters are named

## 3. Entry point detection

- [x] 3.1 Add the entry point tool that reads the raw module reports of the project and returns, per class, the
      module, the code category, whether the class declares a main method, its annotation names and its implemented
      interfaces; verify with a unit test over a module report that the class of a main method is reported and that
      an instance method named `main` and a static method named `main` with a return type are not
- [x] 3.2 Order the entry point candidates by module and by class name within a module, and report a module without
      a report as skipped; verify with a unit test that two calls return the same order
- [x] 3.3 Write the entry point evidence as the project level CSV report `EntryPoints/` with one row per class;
      verify with a unit test that the file is written with the fields of the spec
- [x] 3.4 Register the entry point tool in the tool factory under a name of the `java_` family; verify that the tool
      appears in the tool list of the engine and that its parameters are named

## 4. The anchor analysis

- [x] 4.1 Write the response schema `results/ModernisationAnchors.json` as a project level entity list that holds
      the reasoning, the anchors with their type, module, element, evidence, rank and candidate patterns, and the
      anchor types that are absent with their reason; verify that the schema parses, that it declares every property
      it requires, and that a rendered sample answer validates against it
- [x] 4.2 Write the prompt `prompts/modernisation_anchors.md` that asks for the anchors of every type, that names the
      counted evidence each type rests on, that takes the marker knowledge and the pattern catalogue from the marker
      facts, and that states that the strength of an anchor is a rank and not a verdict; verify that the prompt names
      the marker facts, the tools it calls and every anchor type
- [x] 4.3 Add the task `ModernisationAnchors` to `tasks.yaml` with its response property, its dependencies on the
      analyses the anchor types rest on, and a tool whitelist that holds only the tools the prompt calls; verify that
      the task definition validation passes and that the task is active
- [x] 4.4 Make the prompt read the counted evidence and the context map result and not the prose findings of the
      other evaluation analyses; verify with a test that the prompt template of the task holds no reference to the
      findings of the other evaluations
- [x] 4.5 Let the anchor analysis report an anchor type as absent with the reason when the analysis it rests on did
      not run, and let it state the limits of the analysis, among them that no change frequency, no age of the code
      and no ownership is part of it; verify with a test over a result without a context map that the domain anchors
      are reported as absent with the reason

## 5. Report and documentation

- [x] 5.1 Render the anchors in `documentation/Documentation.adoc.hbs` as one table per anchor type, naming the
      anchor, its element, its evidence and its candidate patterns, and rendering an absent type with its reason;
      verify with `DocumentationTemplateTest` that the section is rendered and that it carries its pointer line
- [x] 5.2 Add the entry for the modernisation anchor analysis to `documentation/AnalysisReference.adoc.hbs` with the
      parts every entry carries and the anchor specifics: the anchor types, the evidence each type rests on, that the
      strength is a rank and not a threshold, that a candidate pattern is not a decision, and the marker facts file;
      verify with `AnalysisReferenceTest` that every entry carries all its parts and that the documented analyses have
      exactly one entry each
- [x] 5.3 Add the row for the anchor analysis to the task table of the README of the analysis and point the README at
      the reference; verify with `SoftwareArchitectureTaskConfigTest` that the README points at the reference and that
      the task table holds the new row

## 6. Integration checks

- [x] 6.1 Run `mvn verify` and confirm the whole build passes, that the report format version is 4 after the
      interface flag, and that
      no existing metric changed its result; verify with `RawModuleReportFormatTest` and the metric tests of the
      engine
- [x] 6.2 Run the specification validation of the project and confirm that the change validates; verify with
      `openspec validate add-modernisation-anchors --strict`

## 7. Review follow-up

- [x] 7.1 Add the interface flag to the class record of the raw Java module report and raise the format version to 4,
      so that the seam tool finds an interface that declares a default method or a constant; verify with
      `SeamToolTest` and `RawModuleReportFormatTest` that the flag survives the round trip and that a report of the
      old version is regenerated
- [x] 7.2 Group the rendered anchors by their type in the documentation template, so that the report holds one table
      per anchor type whatever order the model ranked the anchors in; verify with `DocumentationTemplateTest` that a
      second type whose anchor has rank 3 still gets its table
- [x] 7.3 State in the tool description, in the prompt and in the reference entry that the consumers of a seam are
      the classes that reference the interface without implementing it; verify with the tests of the change

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
