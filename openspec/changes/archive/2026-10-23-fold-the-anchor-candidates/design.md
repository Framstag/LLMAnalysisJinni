# Design

## Context

The evidence is the run on `workspaces/maven`:

- The seam candidate list held `org.apache.maven.api.model.InputLocationTracker` at ranks 1, 2 and 3, with the same 230
  implementors, once for each declaring module the aggregator and its inner modules report.
- The entry point candidate list was held by two JMH benchmark harnesses with a `main` method at ranks 1 to 6, because
  the current order ranks by the number of annotations, and a benchmark class carries several.
- The entry point analysis and the seam analysis report per module on purpose: a class belongs to the module whose
  report holds it, and the hotspot ranking was folded the same way for the same reason.

## Goals / Non-Goals

**Goals:**

- One candidate per element, with the modules it was seen in.
- Production entry points ahead of test and generated ones.
- The two underlying analyses unchanged.

**Non-Goals:**

- A change of what the entry point and seam analyses count.
- A change of the report format or of the CSV reports.
- A change of the budget property or of the anchor types.

## Decisions

### The fold happens in the candidate tool, not in the analyses it reads

The entry point analysis and the seam analysis count per module, and that count is right: a class belongs to a module
report. The projection of that count onto the anchors of the project is what folds the appearances. Doing the fold in
the analyses would change two tools that other analyses read, for a concern only the anchors have.

*Alternative considered:* fold in the entry point and seam tools. Rejected: their results are per module evidence, and
a caller that wants the per module view would lose it.

### The candidate names one module and the modules it was seen in

The primary module is the one whose appearance ranks highest under the order of the analysis: for a seam the declaring
module with the most implementors, then the most consumers, then the alphabetically first; for an entry point the
module of the appearance with the strongest evidence. The other modules are carried beside it, so the reader sees that
the element is not owned by one module and a chart of the project does not count it three times.

*Alternative considered:* carry a list of modules and no primary module. Rejected: the anchor schema names one module
per anchor, and a candidate without a primary module would leave the analysis to choose one, which is the choice the
tool can make with the counted evidence in hand.

### The entry point order puts the code category first, then the evidence

Production code first, then test and generated code; within a category the declared `main` method, then the annotation
count, then the interface count. The category leads because a benchmark harness is not a place a modernisation can
attach to, however many annotations it carries.

*Alternative considered:* drop test and generated entry points. Rejected: the list states what it holds, and dropping
them silently would hide that the project has them. They stay, below, and their evidence says which category they are.

## Risks / Trade-offs

- **A fold can hide a module that owns the element.** → The candidate names every module it was seen in, so nothing is
  lost; only the duplicate rank is gone.
- **The primary module of a folded candidate is a rule, not a fact.** → The rule is stated, the modules are all named,
  and the evidence of the candidate carries the counts the rule rests on.
