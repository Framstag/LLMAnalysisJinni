# Design

## Context

See proposal.md for the motivation. What shapes the approach:

- The raw Java module report is versioned, and `JavaTool` already regenerates a report whose version is behind the
  current one. A change of the class record is therefore a change of the version, and the regeneration path exists.
  The current version is 4; version 4 added the interface flag of a class, and this change adds the source file on
  top of it.
- The class record today holds the qualified name, the documentation, the annotations, the methods, the super class,
  the interfaces and the fields of a class. It does not hold the file the class was declared in, and the file is what
  the history counts.
- The counted complexity of a class is already in the report: the cyclomatic complexity of its methods and the number
  of its methods. The hotspot join needs no new complexity count.
- The counted version history is per file, not per class. A class is declared in one file, and the class record of the
  report is the join between the two.
- The reports of the engine are written to the working directory of the workspace, one directory per report type.

## Goals / Non-Goals

**Goals:**

- The change frequency and the complexity of a class, joined and reported beside each other.
- A ranking the reader can disagree with, because both figures are reported next to the rank.
- A project without a counted history reported as absent, not as a project without hotspots.
- A change that leaves the results of the existing metrics untouched.

**Non-Goals:**

- A judgement about a class. The hotspot analysis counts and ranks; the evaluation task reads the counts and says
  what they mean.
- The history of a class that was moved between files. The join follows the file the class is declared in now.
- A per author view. The history holds the authors; the hotspot does not rank by author.
- A change of the counted window or of the counted history. The history is counted by its own change.

## Decisions

### The class record carries the source file, and the report format version rises

The join between the history and a class needs the file of the class, and the file is not in the report today. The
class record gains the path, the format version rises to 5, and the reports of the existing workspaces are
regenerated once by the mechanism that already exists.

*Alternative considered:* the hotspot tool resolves the file of a class itself, by looking for the source file of the
qualified name. Rejected: a class name does not tell its file, a file may hold several classes, and the resolution
would fail for the nested and the generated classes. The record is the place that already knows the answer.

*Alternative considered:* keep the version and let the hotspot tool read the history per file and match it to the
classes by the file it finds. Rejected: the same resolution problem, in a place where it is harder to see.

### The complexity is taken from the counted metrics, not counted again

The report already holds the cyclomatic complexity and the method count of a class. The hotspot tool reads them.

*Alternative considered:* count the complexity inside the hotspot tool. Rejected: two counters for one number, and the
two could disagree. The counted metrics are the auditable source.

### The rank is the two figures together, and both are reported next to it

The classes are ordered by change frequency and complexity together, and the result reports both figures beside every
rank, so that a reader who weights them differently can reorder the list without counting anything again. No
threshold decides the outcome, and the result says that the order is a rank among the classes of this project.

*Alternative considered:* a product of the two figures as a single score. Rejected: a single number hides the
weighting, and the two figures mean different things. The report can show both.

### A class that never changed is not a hotspot

The join needs both figures. A class that was never changed has no change frequency, and it is not a hotspot, however
complicated it is. It is also not evidence that the class is dead: the file may simply be new.

*Alternative considered:* counting a never changed class with a change frequency of zero and ranking it last.
Rejected: that would put the newest code at the bottom of a list that is about the code that hurts, and it would blur
the difference between "never changed" and "not in the counted window".

### A project without a counted history has no hotspots, and says so

The hotspot tool reads the counted history. When the history cannot be read at all - the project is not a
repository, or the machine holds no git executable - the tool reports the hotspots as absent with the reason of the
absent history, and the report renders that absence instead of an empty table. A TRUNCATED history is not that
case: the copy of the repository holds the recent commits, so the hotspots of the counted window are reported and
the truncation is named.

*Alternative considered:* report a project with an empty hotspot list. Rejected: an empty list reads as "this project
has no hotspots", which is a different statement from "this project has no history to judge".

*Alternative considered:* report no hotspots for a truncated history as well. Rejected: the recent history of a
shallow clone is the history of the project as far as the clone reaches, and dropping it would throw away the most
recent, and most relevant, part. The classes changed before the window are left out and the reader is told so.

### The hotspot gets its own chapter, beside the anchor chapter

The hotspot is a metric of the code, the anchor is a judgement about where a modernisation can attach. The report
already separates the two kinds: the per module metric evaluations are one part of the report, the project level
judgements are another. The hotspot is a metric, so it gets a chapter of its own, and the strategy analysis reads
both.

*Alternative considered:* the hotspot as an anchor type in the anchor chapter. Rejected: the anchor analysis is
defined over the anchor types that rest on the evidence the anchor change has, and the hotspot needs the history and
the format change to exist. Keeping the hotspot apart lets the anchor change land without it.

## Risks / Trade-offs

- **Every existing workspace regenerates its raw reports once.** The reports are large and the regeneration parses
  the modules again. → The regeneration path already exists and reports what it did, and the regeneration happens once
  per report.
- **The change frequency of a class is the change frequency of its file.** A file that holds a class and a lot of
  other code gives that class the change count of the file. → The reference entry states this, and the hotspot
  evaluation states it as well.
- **A class that was moved between files inherits the history of its new file.** → The reference entry names this
  limit, and the counted window bounds what the move can carry.
- **The counted window decides which classes are alive.** A project with a long history and a small window shows only
  the recent life. → The result names the window, and the reference entry names it as the bound of the change
  frequency.
- **The rank is a judgement of two figures with an implied weighting.** → Both figures are reported next to every
  rank, and the evaluation task is told to read the figures and not only the order.
