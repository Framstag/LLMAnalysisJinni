# Proposal

## Why

The analysis reaches dependency data through one road only: a CycloneDX SBOM. A project that never produces one gets
no dependency list, no technology stack and no technology-marker anchors, even though the evidence for all three sits
in the source the analysis has already parsed.

Measured on two projects in `workspaces/`:

| project | SBOM | external namespace prefixes in the parsed imports | build files on disk |
|---------|------|--------------------------------------------------|---------------------|
| Apache Maven (reactor) | none | 19 groups under 5 roots | 2046 `pom.xml`, of which the reactor uses 4-5 |
| JabRef | 421 components | 68 groups under 19 roots | Gradle |

The build-file road does not close the gap honestly. Apache Maven's reactor root inherits from `maven-parent:50`, which
is outside the project, so most dependency versions are not in the tree and cannot be resolved without a warm local
repository — the same prerequisite the build tool itself has. A scanner that walks the reactor would emit an empty
version exactly where the end-of-life table of the marker facts needs one; an LLM reading the poms would emit a
plausible one. Neither is acceptable, and the engine should not execute the analysed project's build tool.

The import distribution is already parsed, already cached, already deterministic, and a hundred times smaller than
either road. It carries no versions — and the half of the marker knowledge that matters here does not need them: the
legacy namespace table is keyed by namespace, and an import is a namespace.

## What Changes

- The engine consolidates the import diversity evidence of a project into one list of external namespace prefixes,
  grouped by their first segment: production code only, platform and type-shaped entries removed, the project's own
  namespaces removed, bounded, with the number of namespaces the project holds beyond the bound stated.
- A new analysis classifies those groups into technologies. It answers with names and the namespaces they rest on.
  It claims no version at any point, and it names the groups it could not place instead of guessing.
- The technology-marker anchors gain a namespace half: a marker that the marker facts key by namespace is reported
  from the consolidated evidence, and its evidence names that source. The version-range half is reported as
  unavailable, with the reason, whenever there is no dependency data.
- The bound of the consolidated list is a workspace property, following the shape of the existing anchor budget.

Explicit non-goals, because they are the road not taken: no dependency versions, no license evaluation, no transitive
graph, no parsing of `pom.xml` or Gradle files, and no execution of the analysed project's build tool.

## Capabilities

### New Capabilities

- `technology-evidence`: what the analysis may claim about the technology of a project without an SBOM — the
  consolidated external namespace evidence, its filtering and bounding rules, the classification into technologies,
  and the rule that no version is ever claimed from it.

### Modified Capabilities

- `modernisation-anchor-detection`: the technology-marker anchor type is currently reported absent whenever the SBOM
  was not loaded. Its namespace-keyed half does not need dependency data, so it changes to be reported from the
  namespace evidence, with the version-range half reported absent and the reason naming what is missing.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/JavaTool.java`: one new tool method over the already cached module
  reports.
- `src/main/java/com/framstag/llmaj/config/Config.java`: one bound property, following `anchor.candidatesPerType`.
- `analysis/software-architecture/`: new response schema, new task, new prompt, a row in the task table of the
  analysis README, and an edit to `prompts/modernisation_anchors.md`.
- `analysis/software-architecture/facts/modernisation_markers.md` is not changed: the marker knowledge stays in the
  facts, and no namespace, version range or pattern moves into a tool.
- Unchanged: `import-diversity` (the per-module prefix report is read, not altered), `sbom-optional-analysis` (the
  SBOM path keeps its current behaviour, including the empty result and its reason), license evaluation and the
  dependency diagram.
