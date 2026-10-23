# Design

## Context

See proposal.md for the motivation. What shapes the approach:

- The engine already writes a raw Java module report per build module. It holds, per build unit, the imports of the
  unit, the class references of the unit with their api width and traffic, and per class the annotations, the
  methods, the super class and the interfaces. The two new counts need nothing beyond that report.
- The report format is versioned (`Module.CURRENT_REPORT_FORMAT_VERSION`) and `JavaTool` regenerates a
  report whose version is behind. A change to the format is therefore possible, but it is a cost: every report in
  every existing workspace is regenerated.
- The `facts/` directory holds static knowledge and is read by prompts through Handlebars partials. No Java tool
  reads `facts/`, and none of them knows a framework.
- The project counts in tools and lets the model judge. The domain vocabulary analysis is the precedent: the
  counted evidence is written by a tool, so that every number the model reasons over can be audited by a human.
- The analysis state is one `analysis.json` per workspace, and a workspace runs one analysis domain. A second
  domain would be a second run over the same project with a second report.
- The last four changes fixed oversized per-module prompts. The size of an input is a concern the project already
  has to defend against.

## Goals / Non-Goals

**Goals:**

- One ranked list of anchors per anchor type, each anchor carrying the evidence it rests on and the candidate
  patterns that fit it.
- The counted evidence behind every number an anchor states, written to disk so a reader can check it.
- An input size that does not grow with the number of findings of the project.
- A change that adds no dependency and touches no existing metric. The change does raise the version of the raw
  report format, because the class record has to carry the flag that says whether a type is an interface.

**Non-Goals:**

- A strategy decision. The change names candidate patterns; it does not choose one, and it does not weigh two
  patterns against each other.
- The data ownership anchor. Who owns a table or a topic is not in the model of the engine, and inventing a
  persistence model for it is a change of its own.
- The hotspot anchor. Change frequency, age of the code and ownership need the version history of the project, which
  the engine does not read today. The report format bump that the class level of that analysis needs belongs to that
  change and not to this one.- A per run history of the anchors. The anchors are a reading of the project as it is now.

## Decisions

### The anchors arrive as a chapter of the existing report, not as a second analysis domain

A second domain would give the modernisation a document of its own, but it would also mean a second run over the
same project, a second task graph, a second set of prompts and a second rendering path, and the anchors would lose
the other 41 tasks they rest on. The chapter costs one task, one prompt, one schema and one rendering section.

*Alternative considered:* a separate `analysis/modernisation/` domain. Rejected for now; the split can still be made
later without changing the tools, because the tools hold no domain knowledge.

### The tools count, the model judges, and the tools know no framework

The seam tool counts implementors and consumers, the entry point tool counts classes, main methods, annotations and
implemented interfaces. Neither tool knows that a `@RestController` is an entry point or that `javax.persistence`
marks a legacy dependency. That knowledge lives in the marker facts and reaches the model through the prompt, where
the judgement belongs.

*Alternative considered:* let the tools read the marker facts and classify. Rejected: the tools would carry the
knowledge of a framework version, and the facts would stop being the single place where that knowledge lives. The
cost of the chosen way is that a class with an entry annotation is only an entry point once the model has looked at
the annotation list in the facts.

### The anchor analysis reads counted evidence and the context map result, and nothing else

The inputs are the tool counts, the inter-module matrix, the split candidate ladder, the test coverage reports, the
import distribution, the dependency list and the context map result. The prose findings of the other evaluation
analyses are not read. They are the opinion of an earlier model call about a single metric, they are the largest
part of the analysis state, and a modernisation anchor that rests on them inherits their wording and their number of
words.

*Alternative considered:* read the findings of the other evaluations to pick up their judgement. Rejected: the size
of the anchor input would then grow with the number of findings, which is the failure the last four changes removed
from the per-module prompts.

### The response is a project level entity list, not a module evaluation

The response of the anchor analysis is a list of anchors, each with a type, a module, an element, the evidence, the
rank and the candidate patterns. It follows the shape of the context map result, which is also a project level
entity list, and not the shape of `ModuleBatchEvaluation`, which is a list of findings per module. An anchor is a
thing of the project, not a finding about a module.

*Alternative considered:* reuse `ModuleBatchEvaluation` and express the anchors as findings per module. Rejected: it
would force the anchor types that are not per module, the seams and the domain anchors, into a per module shape they
do not fit, and it would lose the element an anchor points at.

### The strength of an anchor is a rank within the project

The anchors of a type are ordered by the evidence they rest on, and the result says that the order is a rank among
the anchors of that project. No threshold decides the outcome and no anchor is marked good or bad. This is the stance
the god class ranking, the split candidate ladder and the context map already take, and a modernisation anchor that
carries a verdict would tell the reader more than the evidence supports.

### A candidate pattern is a pattern that fits, and the catalogue bounds it

The candidate patterns are taken from the catalogue in the marker facts: the patterns the project supports are
strangler fig, branch by abstraction, extraction of a module, an anti-corruption layer, a horizontal lift and a
retirement. An anchor for which no catalogue pattern fits carries an empty list and the reason. Every candidate
carries what would make it the wrong choice, so that the reader can disagree with the analysis instead of having to
unravel it.

*Alternative considered:* let the model name any pattern it knows. Rejected: the same project would be reported with
a different set of patterns on every run, and the reader could not tell an invented pattern from a considered one.

### An anchor type whose input is missing is reported as absent

The domain anchors rest on the context map, the cut point anchors on the split candidates, the build unit anchors on
the inter-module matrix and the test coverage anchors on the test coverage reports. When one of these did not run,
the anchor analysis says that this type is absent and why, instead of dropping the type. The SBOM absence is the
precedent: a missing input is a result, not a failure of the run.

### The class record carries the interface flag, and the report format version rises

The seam tool has to tell an interface from a class, and the report does not hold that fact today. Without it the
tool would have to guess from the shape of the class - no method, no field, no supertype but `java.lang.Object` -
and that guess misses every interface that declares a default method or a constant. A missed interface is a missed
seam, and an interface with behaviour of its own is exactly the interesting kind. The class record therefore gains
the flag, the format version rises, and the reports of the existing workspaces are regenerated once by the
mechanism that already exists.

*Alternative considered:* guess the interface from the shape of the class. Rejected: it is wrong exactly where the
seams are interesting.

*Alternative considered:* resolve the interface in the tool from the qualified name of the type. Rejected: a name
does not say what a type is, and the resolution would have to run over every type of the project again.

### The tools are new classes, not more methods on the Java tool

`JavaTool` is already the largest class of the engine. The seam and the entry point analysis read the module reports
and write CSV reports of their own, and they have no business inside the tool that owns the class level metrics.

*Alternative considered:* add the methods to `JavaTool`. Rejected: it would grow a class that is already too large,
and the two analyses share no code with it.

### The CSV reports are project level, one file per analysis

The seam evidence and the entry point evidence are written to `Seams/` and `EntryPoints/` with one file each. The
report directory rule already covers a project level report that carries one view; the two new reports carry one view
each, so no new naming rule is needed.

## Risks / Trade-offs

- **The anchor set is only as good as the counted evidence.** A seam that no class implements because the parser
  missed it is a seam that is not reported. → The tools report the counts they counted, and the anchor analysis states
  that the anchors rest on the Java modules alone.
- **The marker facts go stale.** A legacy namespace that is renamed, or a framework version that reaches its end of
  life after the facts were written, is not noticed. → The facts file names what it is and when it was last reviewed,
  and the analysis states the limits of the marker knowledge.
- **The judgement of the model decides what an anchor is worth.** Two runs of the same project may rank the anchors
  differently. → The rank rests on the counted evidence, the counts are written to disk, and the result names the
  source of every number.
- **The anchor analysis adds one model call to a run.** → The task reads bounded inputs, so the call is one and its
  prompt does not grow with the project.
- **The seven anchor types rest on analyses of uneven quality.** The context map and the domain vocabulary are
  experimental, the metrics are stable. → The anchor result names the type and the analysis each anchor rests on, so
  that the reader knows how firm the ground is.
