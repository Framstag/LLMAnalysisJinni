# Design

## Context

See `proposal.md` — Why. The current state that shapes the approach:

- `JavaTool` parses every module once and caches the reports. Its per-module import diversity report holds three
  `Distribution` objects — production, test and generated code — whose entries are package prefixes with an
  ` (internal)` / ` (external)` label, plus the import count.
- The prefix of an entry is the first two segments of a package (`JavaTool`, the prefix folding). That fold cannot
  tell `package.subpackage` from `package.Type`, and it does not know which prefixes are platform.
- The per-module CSV holds one merged map of all three categories, so a reader of the CSV cannot tell production
  imports from test imports.
- `AnchorCandidateTool` already builds technology marker candidates from the SBOM dependencies *and* from the import
  distribution, each with its own `source`, and reports `absentSources` when a source is missing. Its import
  candidates currently consume the raw prefixes, including the ones that name a type.
- The marker knowledge lives in `facts/modernisation_markers.md`, and the anchor specification forbids the tools from
  holding a legacy namespace, a version range or a pattern.
- Measured for this design: the consolidated list is 19 groups under 5 roots for the Apache Maven reactor and 68
  groups under 19 roots for JabRef. The prefix report fragments one technology across many entries — `javafx` alone
  holds 13 entries, `picocli` 5.

## Goals / Non-Goals

**Goals:**

- One deterministic fold that turns the counted import distribution into a list small enough for a prompt and clean
  enough that a classifier is asked about packages rather than about type names.
- A classification whose output schema has no version property, so a version cannot be reported even by accident.
- The technology marker reported in two halves, each with its own source and its own absence.

**Non-Goals:**

- Changing the per-module import diversity report, its three distributions, its CSV or its specification. The
  consolidation reads the report and does not alter it.
- Any change to the SBOM path, the dependency list, the technology stack task or the license evaluation.
- Extracting dependency data from `pom.xml`, `*.gradle` or any build file, and executing the analysed project's
  build tool. See `proposal.md` for why each is rejected.

## Decisions

### D1. The fold is a tool method on the Java tool, not a new tool class

The fold reads the import diversity reports that the Java tool has already counted and cached. A second tool class
would either duplicate that cache or need the Java tool injected, and it would add a tool namespace for one method.
The existing name pattern already covers this: the report it folds is `java_get_import_diversity_report`.

Alternative considered: a `TechnologyEvidenceTool` that receives the Java tool, mirroring `AnchorCandidateTool`. That
shape is right for a tool that aggregates *several* tools; this one aggregates one. Extract it later if a second
consumer appears.

### D2. The fold reads the three distributions, not the CSV

The CSV merges production, test and generated imports into one map, so an implementation that reads it cannot honour
the requirement that only production code is evidence. The distributions in the report carry the category in their
name, which makes the selection a stable, testable lookup rather than a convention.

### D3. An entry that names a type is folded into its root group, not dropped

Dropping `picocli.CommandLine` would remove `picocli` from the evidence entirely, because a library that exposes one
package and is used through one class appears in the prefix report with nothing but type-shaped entries. The entry
therefore contributes to the group of its first segment, and the type name is never reported.

Array descriptors (`[Lorg.…`) are dropped outright: they are an artefact of how a type was written, they carry no
package information beyond the segment after the descriptor, and their counts are single digits.

### D4. The evidence is grouped by first segment

Measured fragmentation is the reason: grouping turns 68 entries into 19 groups for JabRef and 19 into 5 for the
reactor, while the prefixes stay visible as children of their group. A classifier that receives the flat list has to
merge `javafx.animation`, `javafx.application`, `javafx.beans`, `javafx.collections` and nine more itself, and a
classifier that receives only the roots loses the distinction between `org.apache.maven` and `org.apache.commons`.

Alternatives considered: three-segment prefixes, which keep the resolution but do not fold `javafx` and grow the list
instead of shrinking it; roots only, which is smaller but coarse exactly where the ecosystems are largest.

### D5. The platform filter is a namespace list, and `javax` is not on it

The removal list is `java.`, `jdk.`, `sun.` and `com.sun.`. `javax.` and `jakarta.` are kept, because the legacy
namespace table of the marker facts is keyed by them — filtering `javax` would remove the very evidence the anchor
change is for. Matching is by root segment, so `javafx.…` is not mistaken for `java.…`.

### D6. The classifier's schema holds no version property

Absence of a claim is enforced by the shape of the answer, not by an instruction in the prompt. The schema of the
classification holds a technology name, the namespaces it rests on, a reasoning, an unplaced list and a limit
statement. A model that wanted to state a version would have no field to put it in, and the validator would reject the
answer.

Alternative considered: a `version` field that the prompt tells the model to leave empty. Rejected — a field that
exists is a field that gets filled, and the empty one is indistinguishable from the unknown one.

### D7. The anchor candidates are rebuilt from the consolidated evidence

`AnchorCandidateTool` currently takes its namespace candidates from the raw prefix entries. Pointing that source at the
consolidated evidence removes the type-shaped and array entries from the candidate list as well, so the anchor budget
is not spent on `[Lorg.apache` and the junk does not reach the anchor prompt. The candidates keep their `source`, so
the anchor can still name what counted them.

This is the only place where the consolidation is consumed twice — by the classification task and by the anchor
candidates — and it is why the fold belongs to a tool rather than to a prompt.

### D8. Two workspace properties bound the evidence

`technologyEvidence.groups` bounds the number of root groups, and `technologyEvidence.prefixesPerGroup` bounds the
prefixes carried per group, each with a stated count of what lies beyond the bound. Two properties because a project
with many roots and a project with a few very broad roots grow along different axes, and one knob cannot bound both.

Both follow the shape the configuration already uses for the anchor budget and the context map.

### D9. The anchor prompt names both halves of the technology marker

The type is not marked absent when only the version range half is missing. The prompt states which half rests on which
source, and the version range half is reported unavailable with the reason. The marker knowledge itself stays in the
facts: the tool supplies namespaces and counts, the analysis applies the legacy namespace table, and no tool learns a
framework name.

## Risks / Trade-offs

- [A root group is coarser than an artifact, so `org.apache` covers Maven, plexus and commons] → The prefixes stay as
  children of the group and the classification states the limit; the schema asks for a technology name, never an
  artifact name.
- [The classifier places a namespace that no longer means what its name suggests] → Unplaced groups are reported as
  unplaced; the classification names the namespace it rests on, so a reader can check the claim.
- [The type-shaped fold puts an ambiguous library into a root group with an unrelated one] → The group carries the
  prefix it came from, and the classification sees the group's children, not only the root.
- [The fold silently changes what the anchor candidate list holds] → The candidate evidence text names the source, and
  a test asserts that an array descriptor and a type-shaped entry no longer produce candidates.
- [A project whose modules were never parsed gets an empty evidence and an empty classification] → Both carry a reason
  that names the missing input, so an empty result is a finding and not a silence.

## Migration Plan

No migration. The change adds a tool method, a schema, a task and two properties, and edits one prompt. A workspace
created before the change runs the new task on its next `analyse` run, because the task is active and its inputs are
already present in the state of an analysed project; nothing in `analysis.json` or `state.json` changes shape.

## Open Questions

- Whether the rendered document gains a section for the classification. It is deferrable: it changes neither the
  specs, the approach nor the task breakdown of this change, and it can be added as its own task once the result has
  been seen on a workspace.
