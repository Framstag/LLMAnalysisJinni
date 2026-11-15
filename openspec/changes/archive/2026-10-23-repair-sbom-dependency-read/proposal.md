# Proposal

## Why

The SBOM dependency read finds the dependencies of an application by looking up one entry of the SBOM's dependency
graph: the entry whose reference equals the `bom-ref` of `metadata.component`. That entry exists in an SBOM that the
CycloneDX Maven plugin writes, and it does not exist in an SBOM that the CycloneDX Gradle plugin writes for an
aggregator build, which emits one graph entry per module project instead.

Measured on `workspaces/jabref`, whose project ships a CycloneDX Gradle-plugin SBOM:

```
components            421 libraries
dependencies entries  421
entries whose ref is the aggregator root bom-ref   0
```

The read therefore returned an empty list, and three consumers recorded that emptiness as a property of the project:

| recorded in `analysis.json` | what it says |
|-----------------------------|--------------|
| `dependencies` | "the tool returned an empty list of dependencies, indicating that the application has no direct dependencies recorded in the SBOM" |
| `technologyStack` | "No dependencies were detected in the SBOM; therefore the technology stack cannot be determined" |
| `licenseEvaluation` | populated — it reads the components, not the graph |

Both statements are false about the project, and the first is a reason the model had to invent, because the tool
returns a bare empty list with no channel to say why. `workspaces/spring-petclinic`, whose SBOM comes from the Maven
plugin, reads correctly with the same code — which is why the existing tests, all of which load this project's own
Maven-plugin SBOM, never saw it.

The same read feeds the technology marker anchors through the anchor candidate tool, so the defect removes an anchor
type as well, and the false reason is rendered into the document.

## What Changes

- The dependency read resolves the root of the dependency graph instead of requiring one exact entry: the entry of the
  root component when it exists, otherwise the aggregator's project module entries, otherwise the libraries the graph
  holds. It states which route it took.
- A read that yields nothing states why in its result, so no consumer has to invent a reason. "The project holds no
  dependency" and "the graph of this SBOM could not be resolved" become two different answers.
- The components that are the project's own modules are not reported as dependencies of the project.
- The behaviour for a project without an SBOM is unchanged, and the difference between the two absences stays visible.

## Capabilities

### New Capabilities

- `sbom-dependency-read`: how the dependencies of a loaded SBOM are read — the resolution of the root of the graph, the
  route the read took, the exclusion of the project's own components, and the reason a read that yields nothing
  states.

### Modified Capabilities

- `sbom-optional-analysis`: its requirement "An absent SBOM is a result, not a failure" carries the scenario *Present
  SBOM is unaffected — the location, load and dependency tasks SHALL behave as they do today*. That scenario freezes
  the current dependency read, which is the defect. It changes to state the behaviour the read is meant to have when
  the SBOM is present.

## Impact

- `src/main/java/com/framstag/llmaj/tools/sbom/SBOMTool.java`: the dependency read and the shape of its result.
- `src/main/java/com/framstag/llmaj/tools/sbom/AppDependency.java` and the result type around it, which gains the
  route and the reason.
- `src/main/java/com/framstag/llmaj/tools/java/AnchorCandidateTool.java`: it reads the dependency list and turns the
  "not loaded" case into an absent source; it has to carry the new reason through.
- `analysis/software-architecture/prompts/`: the dependency list, the technology stack and the license evaluation
  name the tool result they read.
- Tests: a checked-in fixture of an aggregator SBOM, and a test that a graph without the root entry still yields the
  dependencies.
- Unchanged: the location and load tasks, the license evaluation's source, the dependency diagram, and every Java
  metric analysis.
