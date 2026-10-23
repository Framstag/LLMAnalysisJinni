# Tasks

## 1. The response shape

- [x] 1.1 Write the response schema of the modernisation strategy as a project level entity list that holds the
      reasoning and the areas, and each area holds its module, its element, the anchor type it rests on, its patterns
      with the evidence for and against them and the figures of its cost; verify that the schema parses, that it
      declares every property it requires and that a rendered sample answer validates against it
- [x] 1.2 Let the schema carry the patterns of an area as a bounded list of the catalogue, and let it carry the
      figures of the cost by name; verify with a test that an area with a pattern outside the catalogue does not
      validate against the schema

## 2. The prompt and the task

- [x] 2.1 Write the prompt that asks for the modernisation areas of the project, that names the evidence every area
      rests on, that takes the pattern catalogue from the marker facts, that states that the cost rests on the counted
      figures and that the order is not a decision; verify that the prompt names the marker facts, the inputs it reads
      and the limits of the analysis
- [x] 2.2 Make the prompt read the anchor result, the hotspot result and the counted evidence and not the prose
      findings of the other evaluation analyses; verify with a test that the prompt template of the task holds no
      reference to the findings of the other evaluations
- [x] 2.3 Add the task that runs the strategy analysis to `tasks.yaml` with its response property, its dependencies on
      the anchor and the hotspot analyses and a tool whitelist that holds only the tools the prompt calls; verify that
      the task definition validation passes and that the task is active
- [x] 2.4 Let the analysis report an area type as absent with the reason when the analysis it rests on did not run,
      and let it state the limits of the analysis; verify with a test over a result without hotspots that the areas
      that rest on the hotspots are reported as absent with the reason

## 3. Report and documentation

- [x] 3.1 Render the modernisation areas in `documentation/Documentation.adoc.hbs` as one table per area, naming the
      area, its patterns, the evidence and the figures of the cost, present them in the order of the counted cost and
      name no area as the one to start with; verify with `DocumentationTemplateTest` that the section is rendered and
      that it carries its pointer line
- [x] 3.2 Add the entry for the strategy analysis to `documentation/AnalysisReference.adoc.hbs` with the parts every
      entry carries and the strategy specifics, among them the figures the cost is made of, that the order follows the
      counted cost and that the business value of a change is not part of the analysis; verify with
      `AnalysisReferenceTest` that every entry carries all its parts and that the documented analyses have exactly one
      entry each
- [x] 3.3 Add the row for the strategy analysis to the task table of the README of the analysis; verify with
      `SoftwareArchitectureTaskConfigTest` that the task table holds the new row

## 4. Integration checks

- [x] 4.1 Run `mvn verify` and confirm the build passes and that no existing analysis changed its result; verify with
      the tests of the engine
- [x] 4.2 Run the specification validation of the project and confirm that the change validates; verify with
      `openspec validate add-modernisation-strategy --strict`

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
