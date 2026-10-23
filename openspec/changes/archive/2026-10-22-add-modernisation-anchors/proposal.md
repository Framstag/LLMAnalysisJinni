# Proposal

## Why

The analysis already holds the raw material for architectural modernisation and never joins it: it knows the
bounded contexts of the project, the cheapest cuts of a module, the coupling between modules, the interfaces a
module implements, the imports a module pulls in and the versions a module depends on. A modernisation pattern -
strangler fig, branch by abstraction, extraction, an anti-corruption layer, a retirement - all need the same thing
first: a place to attach. Without that place, every modernisation decision is argued from opinion, and the report
cannot say which part of the system is a seam and which part is a wall.

## What Changes

- Count the seams of a project: the interfaces of the project with the classes that implement them, the modules
  that implement them and the modules that consume them, so that a replaceable port can be told apart from an
  interface nobody uses.
- Let the class record of the raw Java module report carry the flag that says whether a type is an interface, and
  raise the format version of the report, so that an interface with a default method or a constant is not read as a
  class.
- Count the entry points of a project: the classes that carry a `main` method, the classes that carry a framework
  entry annotation and the classes that implement a service provider interface, so that the places a caller can
  reach a module are known.
- Add a project-level anchor analysis that joins the seams, the entry points, the context map, the inter-module
  dependency matrix, the split candidate ladder, the test coverage and the import and dependency data into one
  ranked list of anchors, each anchor carrying the evidence it rests on and the modernisation patterns that fit it.
- Add the marker knowledge the judgement needs as static facts: the namespaces that mark a legacy dependency, the
  version ranges that mark an end of life dependency, the annotations that mark an entry point, the interfaces that
  mark a service provider interface, and the catalogue of modernisation patterns an anchor may be matched against.
- Render the anchors as a chapter of the generated report, with a table per anchor type and the evidence and
  candidate patterns beside it, and describe the analysis in the analysis reference.
- Write the counted evidence of the seam and entry point analyses as CSV files, one row per fact.

The anchor analysis is a chapter of the existing report. It is not a separate analysis domain, and it changes no
report format, and it changes no existing metric.

## Capabilities

### New Capabilities

- `seam-detection`: the counted seams of a project, the interfaces with their implementors and consumers, and the
  distinction between a port that can be replaced and an interface nobody uses.
- `entry-point-detection`: the counted entry points of a project, the classes that carry a main method, a framework
  entry annotation or a service provider interface, with the module and the code category they belong to.
- `modernisation-anchor-detection`: the anchor types, the evidence each anchor type rests on, the ranking of the
  anchors, the candidate modernisation patterns an anchor is matched against, and the chapter that renders them.

### Modified Capabilities

- `class-dependency-graph`: the class record of the raw module report carries the flag that says whether a type is
  an interface, and the format version of the report rises.
- `analysis-reference`: the documented set of analyses gains the modernisation anchor analysis, and the reference
  gains an entry that states what an anchor is, what its evidence counts and what a candidate pattern means.
- `documentation-coverage`: the report renders the modernisation anchor section, carrying the pointer to its
  reference entry.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/`: one project-level tool for the seams and one for the entry
  points, both reading the raw module reports the engine already writes. The class record of the raw report gains
  the interface flag, so the report format version rises and every stored report is regenerated once. No change to
  the Java parser behaviour and no change to the result of an existing metric.
- New project-level CSV report directories for the seam and the entry point evidence.
- `analysis/software-architecture/`: one new task, prompt, response schema, facts file, documentation chapter and
  reference entry, and one row in the README task table.
- The anchor task depends on the context map analysis, the inter-module dependency analysis, the split candidate
  analysis and the test coverage analysis. Without them the anchor types that rest on those results are absent, and
  the anchor analysis reports the anchors it can still support.
