# Tasks

## 1. The consolidated namespace fold

- [x] 1.1 Add `technologyEvidence.groups` and `technologyEvidence.prefixesPerGroup` to `Config` beside the existing
      `anchor.candidatesPerType`, with seeded default values, and verify a `ConfigTest` reads a workspace value and
      falls back to the seeded value when the workspace names none.
- [x] 1.2 Add the consolidation to `JavaTool` as `java_get_import_technology_evidence`, reading the production
      distribution of the cached import diversity reports and folding the entries into groups by first segment.
      Verify that a report with entries of all three categories yields a result built from the production entries
      only, and that a prefix imported by two modules is summed into one entry.
- [x] 1.3 Apply the removal rules in one place with a unit test per rule: the platform prefixes below `java.`, `jdk.`,
      `sun.` and `com.sun.` are removed; `javax.` and `jakarta.` are kept; `javafx.` is kept; an array descriptor is
      dropped; every project namespace is removed, not only the first one found. Verify each rule by a test that names
      the rule.
- [x] 1.4 Fold an entry whose second segment names a type into the group of its first segment, and verify by a test
      that `picocli.CommandLine` yields a `picocli` group and no `picocli.CommandLine` prefix, and that an entry with
      a lower-case second segment keeps both segments.
- [x] 1.5 Bound the result by the two properties and state the total number of groups beside the bounded list.
      Verify by a test that a project with more groups than the bound returns the strongest groups and the total, and
      that a project below the bound returns every group and states that number as the total.
- [x] 1.6 Return an empty evidence with a reason when no module holds a parsed external import, and verify by a test
      that the reason names the missing input and that the result is empty rather than absent.

## 2. The classification and its task

- [x] 2.1 Add `results/TechnologyEvidence.json` with a reasoning, a technology list naming each technology and the
      namespaces it rests on, an unplaced list, and a limit statement. The schema carries no version property.
      Verify that the schema is valid and that `ResponseSchemaContractTest` still passes for the domain.
- [x] 2.2 Add `prompts/technology_evidence.md` stating that the tool supplies groups of namespaces, that a technology
      is named and an artifact is not, that a version is not part of the answer, and that an unplaced group is
      reported as unplaced. Verify the prompt reaches the template factory and renders with the schema description by
      a test that loads and renders it.
- [x] 2.3 Add the `TechnologyEvidence` task to `tasks.yaml` with `responseProperty: technologyEvidence`, the tag
      `technology_evidence`, `dependsOn: module_analysis_reports_all`, `active: true` and a tool whitelist naming only
      `java_get_import_technology_evidence`. Verify that the task definition test accepts the definition and that the
      `dependsOn` tag resolves to an existing tag.
- [x] 2.4 Add the row of the new task to the task table of `analysis/software-architecture/README.md`, keeping the
      table a single Markdown table. Verify with `openspec validate --strict` and by the task-table test that every
      task of `tasks.yaml` has a row.

## 3. The anchor candidates

- [x] 3.1 Point the namespace source of `AnchorCandidateTool.technologyMarkerCandidates` at the consolidated evidence
      instead of the raw prefix entries, keeping the `source` of each candidate and its evidence text. Verify by a
      test that an array descriptor and a type-shaped entry no longer produce a candidate, and that a namespace of the
      consolidated evidence still does.
- [x] 3.2 Verify that the SBOM-sourced candidates are unchanged by the same test class, so that the two sources still
      rank against each other as they do today.

## 4. The two halves of the technology marker

- [x] 4.1 Edit `prompts/modernisation_anchors.md` so that the technology marker is not reported absent when only the
      version range half is missing: the namespace half rests on the consolidated namespace evidence and names it as
      its source, the version range half is reported unavailable with a reason naming the missing dependency data, and
      the type is reported absent only when both halves are missing. Verify by reading the rendered prompt for a
      workspace and checking the three statements are present.
- [x] 4.2 Add a test asserting that the prompt of the anchor task and `prompts/technology_evidence.md` name no legacy
      namespace, no version range and no pattern, so that the marker knowledge stays in `facts/modernisation_markers.md`.

## 5. Integration check

- [x] 5.1 Run `mvn verify` and confirm the build gate passes, including the specification validation and the artefact
      smoke test.
- [ ] 5.2 Run `analyse` on the Apache Maven workspace, whose project holds no SBOM, and confirm its
      `technologyEvidence` names technologies with namespaces, states that no version is part of the result, and that
      the anchor result holds technology markers whose evidence names the consolidated namespace evidence as the
      source.
- [ ] 5.3 Run `analyse` on the JabRef workspace, whose project holds an SBOM, and confirm the SBOM path is unchanged:
      the dependency list and the license evaluation behave as they did before the change.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result and that the specifications of the two capabilities hold the archived requirements.
