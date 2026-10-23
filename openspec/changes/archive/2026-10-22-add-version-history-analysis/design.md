# Design

## Context

See proposal.md for the motivation. What shapes the approach:

- The engine holds the project directory in `AnalysisContext.getProjectRoot()`. A tool that reads the project is
  given the project, the same way the filesystem and the file tools are.
- The project adds no dependency for analysis logic. A history reader that needs a library would add one; the git
  executable of the machine does not.
- The reports of the engine are written to the working directory of the workspace, one directory per report type, one
  file per view for a report that has no module context. The rule already exists in
  `report-directory-structure`, so the history reports need no change to it.
- The engine already has a precedent for a missing input that is a result and not a failure: a project without an SBOM
  is reported as a project without dependency data.

## Goals / Non-Goals

**Goals:**

- The counted history of every file, per module and per pair of files that change together.
- A project whose history cannot be read yields a result that names the reason.
- The counted history written to disk, so that the later analyses that reason over it can be checked.
- A bounded, named window, so that two runs count the same history.

**Non-Goals:**

- Judging the history. Which modules are worth modernising is the work of the anchor analysis and the strategy
  analysis, not of the counter.
- Blaming a person. The history says who changed a file, because that is a fact about the project; nothing in this
  change draws a conclusion about a person.
- The history of a project that is not a repository. There is nothing to count, and the result says so.
- Commit messages, branches, tags and merges as content. The change counts the files, the dates, the lines and the
  authors.

## Decisions

### The history is read with the git executable of the machine, and the project adds no dependency

The git executable is present on the machines that develop the code, and it is the only reader of the history that
does not put a library into the shaded jar. A missing executable is one of the reported cases of an absent history,
so the tool does not fail when it is absent.

*Alternative considered:* a library that reads the repository format directly. Rejected: it would add a dependency
to the build for a counter, and the shaded jar is checked for its contents in the build.

### The tool shells out once per counted window and parses the machine readable output

The tool runs the history reader with the flags that ask for the machine readable form, and parses that form. The
per-file counts, the dates and the authors come out of one pass, and the pairs of files that change together come out
of the same pass.

*Alternative considered:* one call per file. Rejected: the number of calls would be the number of files of the
project, and the cost of the run would be decided by the size of the project.

### The window is a number of commits, named in the result

The counted window is the most recent commits of the project, and the result names how many commits it covered. A
commit count is reproducible: two runs of the same project count the same commits, unless the project changed in
between.

*Alternative considered:* a time span, for example the last two years. Rejected: a time span counts a different
number of commits on every day, so two runs of the same project would not agree, and the tool would have to be run
again for a project that did not change.

### The absence of a history has three named cases

A project that is not a repository, a machine without the git executable and a shallow clone are three different
situations, and the reader can act on each of them differently: the first one is a fact about the project, the second
one is a fact about the machine, and the third one is a fact about the copy. The result names which one it is.

*Alternative considered:* one generic "no history" answer. Rejected: the reader would not know whether the project
has no history or whether the copy on this machine does not.

### A shallow clone is an absence of the older history, not an absence of the history

A shallow clone holds the recent commits, and its recent commits are counted as usual. What is missing is the time
before the oldest commit it holds, and the result says that this part is missing.

*Alternative considered:* treat a shallow clone as no history at all. Rejected: the recent history of a shallow clone
is the history of the project as far as the clone reaches, and dropping it would throw away the most recent, and most
relevant, part.

### The history is counted per file, and the module aggregation is derived from it

The module of a file is known to the engine, so the per module figures are counted from the per file counts rather
than from a second pass over the history. The pairs of files that change together are counted once, in the same pass.

*Alternative considered:* count the history per module directly. Rejected: the per file counts are the evidence the
reader checks, and a per module count that cannot be traced back to the files behind it is the kind of number the
project avoids.

### The tool is a new class, registered in the tool factory

The tool reads the history of the project, writes its own reports and knows nothing about the Java metrics. It joins
the tool factory beside the other tools, under a name of its own family, because the history is not a Java analysis.

*Alternative considered:* add the methods to the existing Java tool. Rejected: the history is not Java analysis, and
the Java tool is already the largest class of the engine.

## Risks / Trade-offs

- **The counted history depends on the copy of the project.** A clone that is behind the working tree counts the
  commits of the clone. → The result names the window it counted, and the count is bounded to the commits of the
  repository the project directory belongs to.
- **A large project makes the counting expensive.** The pass is over the commits of the window, not over the files of
  the project. → The window bounds the cost, and the result names it.
- **The pairs of files that change together grow quadratically with the size of a commit.** A commit that touches
  many files produces many pairs. → The tool counts the pairs of a commit up to a bound and states the bound, so a
  commit that touches an unusual number of files cannot flood the result.
- **The authorship of a module is a fact that can be read as a judgement about a person.** → Nothing in the result
  states a conclusion about a person, and the report renders the counts as counts.
- **The history is not part of the report of the analysis yet.** → The history is counted evidence that the later
  changes read; this change adds no chapter, so nothing about the report changes for a reader until then.
