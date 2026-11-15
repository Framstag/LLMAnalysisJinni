# Design

## Context

See `proposal.md` — Why. The current state that shapes the approach:

- The dependency read takes `bom.getMetadata().getComponent().getBomRef()` and looks that one reference up in a map
  built from `bom.getDependencies()`. A miss returns `List.of()` and writes an error to the log.
- The licence path in the same class reads `bom.getComponents()` and is unaffected by the layout of the graph, which
  is why one half of the report was correct and the other empty.
- `AppDependency` holds `id`, `name` and `version`. A list of it is the whole result of the tool, so there is no field
  in which a reason could travel back to the model.
- The existing tests load this project's own `target/bom.json`, which the CycloneDX **Maven** plugin writes. That
  plugin emits the root entry, so every test walks the direct route and the fallback routes have never been exercised.

## Goals / Non-Goals

**Goals:**

- A dependency list for an SBOM of any layout the CycloneDX plugins produce, with the route that produced it visible.
- A reason that reaches the model, so no consumer has to invent one.
- A regression test that fails on the current code.

**Non-Goals:**

- Reading SPDX or CycloneDX XML — that is a discovery concern and belongs to its own change.
- Deciding direct from transitive dependencies by reading the graph beyond the root. Where the graph cannot tell, the
  result says so instead of guessing.
- Changing the licence evaluation, the location task or the load task.

## Decisions

### D1. The root is resolved through three ordered routes, and the route is part of the result

1. `root` — the graph holds an entry for the reference of `metadata.component`. This is today's behaviour and stays
   first.
2. `project-modules` — the graph holds entries whose reference carries a `project_path` qualifier, which is what the
   CycloneDX Gradle plugin writes for the modules of an aggregator build. The dependencies are the components those
   entries depend on, summed.
3. `components` — no usable graph. The libraries of `bom.getComponents()` are reported, marked as not a direct
   dependency list.

Alternatives considered for the route-2 match: comparing the component group to the root group, which fails on the
measured data — the root group is `org.jabref` while the module components carry `JabRef`; and the
`cdx:maven:package` property, which is a plugin-specific extra rather than a reference. The qualifier lives in the
reference itself, which is the thing the lookup already keys on.

### D2. The result of the read is a record, not a list

A list has nowhere to put a reason, and that is the defect: the tool could not say why and the model filled the gap.
The result gains the route, the reason, the number of components the graph held and the number of project components
left out.

Alternatives considered: a second status tool, which the model can skip and then invent a reason again; keeping the
reason in the log, which is today's behaviour and is what failed.

### D3. The "no SBOM loaded" case becomes a reason instead of an exception

`getApplicationDependencies()` currently throws `IllegalStateException`, and `AnchorCandidateTool` catches it to build
an absent source. With a reason field that catch is no longer needed and the two absences become comparable data
instead of an exception and a nil check. The other tools of the class keep `assertSBOMLoaded()`, because they have
nothing to report and no reason to give.

### D4. A fallback list says what it is

A list from route 3 holds every component of the SBOM, so it cannot distinguish a direct dependency from a transitive
one. The result states that, and the prompt of the dependency list tells the model not to report such a list as the
direct dependencies of the application. Route 2 is a direct-dependency list of the module entries, and states which
modules it summed.

### D5. The project's own components are excluded by the reference qualifier

The same `project_path` qualifier identifies a component as a module of the build, so the exclusion needs no second
rule. The count of what was left out travels in the result, so a reader sees that the SBOM described a build and not
only a set of libraries.

### D6. The fixture is a hand-written aggregator SBOM

`src/test/resources/sbom/aggregator.cdx.json` holds a root component whose reference carries the root project path,
two module components, two library components and a graph with one entry per module and none for the root. The test
points the project root of the `AnalysisContext` at `src/test/resources` so the existing path resolution loads it
unchanged.

Alternative considered: a copy of JabRef's `bom.json`, 940 KB, which would make the test data unreadable and would tie
the test to another project's build.

## Risks / Trade-offs

- [The `project_path` qualifier is a convention of one plugin and not a CycloneDX feature] → Route 3 still yields the
  components, and the route travels in the result, so a plugin that changes its reference format degrades visibly
  rather than silently.
- [A route-3 list is not a direct-dependency list] → The result states it and the prompt forbids reporting it as one.
- [The reason becomes a place to write a novel] → The record carries a short reason and the counts; the prompt of the
  consuming task turns it into prose where a task needs prose.
- [The existing dependency test asserts an exact list and will change shape] → The expected references stay the same
  for this project's own SBOM, so the test asserts the same content through the new result type; that is the check
  that the direct route is unchanged.

## Migration Plan

No migration. The tool signature changes, so every call site is compiled against it; nothing in `analysis.json` or
`state.json` changes shape, and a workspace re-run replaces the recorded empty dependency list with a populated one.

## Open Questions

None. The routes, the result shape and the fixture are decisions this change needs before its tasks can be written.
