# Tasks

## 1. The result of the read

- [x] 1.1 Replace the bare `List<AppDependency>` result with a record carrying the dependencies, the route, the
      reason, the number of components the graph held and the number of project components left out, and verify that
      the record serialises to a tool result the model receives, by a test that inspects the serialised form.
- [x] 1.2 Turn the "no SBOM loaded" case into a result with the route `none` and a reason instead of an exception, and
      verify by a test that a read before any load returns that result and that it names the missing SBOM.
- [x] 1.3 Adapt `AnchorCandidateTool.dependencies()` to the new result: it reads the reason instead of catching the
      exception, and still records the absent source when the read yields nothing. Verify by a test that a workspace
      without a loaded SBOM still produces the absent source entry with a reason.

## 2. The root resolution

- [x] 2.1 Keep the direct route: the entry whose reference equals the reference of the root component. Verify with the
      existing expectation of this project's own `target/bom.json`, which must yield the same references as today
      through the new result, so that the direct route is proven unchanged.
- [x] 2.2 Add the `project-modules` route: when no entry carries the root reference, sum what the entries whose
      reference carries a `project_path` qualifier depend on. Verify by a test on the new fixture that the
      dependencies of both module entries are reported and the route is `project-modules`.
- [x] 2.3 Add the `components` route for an SBOM whose graph is unusable: report the libraries of the components and
      mark the result as not a direct dependency list. Verify by a test on a fixture without a graph that the route is
      `components` and that the reason states it.
- [x] 2.4 Exclude the components that are modules of the analysed project, and report how many were left out. Verify
      by a test on the fixture that its module components are not part of the dependencies and that the count of the
      excluded components is reported.
- [x] 2.5 State the reason in every result whose route is not the direct one, and in every empty result. Verify by a
      test per route that the reason is present exactly then.

## 3. The regression test and its fixture

- [x] 3.1 Add `src/test/resources/sbom/aggregator.cdx.json`: a root component whose reference carries the root project
      path, two module components with their own project path, two library components, and a graph with one entry per
      module and none for the root. Verify that the fixture parses with the CycloneDX parser by loading it in the test.
- [x] 3.2 Add a test that reproduces the defect: load the fixture and assert that the dependency list is not empty.
      Verify that the test fails on the current implementation before the resolution is added — run it once against
      the unfixed read and record that it failed.
- [x] 3.3 Add a test that the stored reason of an empty read names the condition the read reported and does not state
      that the project declares no dependency. Verify by asserting the reason text of the `components` route on a
      fixture whose components hold no library.

## 4. The prompts

- [x] 4.1 Edit `prompts/list_of_dependencies.md` so the model reports the route and the reason of the result, and does
      not report a `components` route as the direct dependencies of the application. Verify by rendering the prompt for
      a workspace and checking both statements are present.
- [x] 4.2 Edit `prompts/description_of_stack.md` and `prompts/analysis_of_licenses.md` so an empty result is explained
      by the reason the tool reported rather than by a statement about the project. Verify by rendering both prompts
      and checking they name the reason of the result.

## 5. Integration check

- [x] 5.1 Run `mvn verify` and confirm the build gate passes, including the specification validation and the artefact
      smoke test.
- [ ] 5.2 Clear the state of the JabRef workspace, run `analyse`, and confirm that its `dependencies` result holds the
      libraries of the aggregator build, that its `technologyStack` no longer states that no dependencies were
      detected, and that its reason names the route when the route is not the direct one.
- [ ] 5.3 Run `analyse` on a workspace whose project has no SBOM and confirm the absent-SBOM behaviour is unchanged:
      the location task is successful, the stored result names the absence, and no task fails for missing data.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify that the archived `sbom-optional-analysis` specification holds the modified requirement with four scenarios,
  and that `sbom-dependency-read` exists as its own capability.
