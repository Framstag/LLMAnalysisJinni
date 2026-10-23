# Design

## Context

See proposal.md for the motivation. What the evidence shows:

- A thread dump of a collection run that had not written a report for twenty minutes shows the parsing thread inside
  `JavaParserTypeSolver.parseDirectory` -> `JavaParser.parse` -> `CommentsInserter.insertComments`, holding the lock of
  the `JavaParser` instance of the solver.
- `JavaTool.createTypeResolver` adds a `JavaParserTypeSolver` per source directory of the module, constructed with the
  default configuration. That configuration attributes comments to the nodes of every parsed file, which is the work
  the dump shows.
- The parse of the module itself is a different parser: `StaticJavaParser` with a `JavaSymbolSolver` over the same
  type solver. Its comment attribution is what produces the documentation of a class and of a method, and a metric
  counts that documentation.

## Goals / Non-Goals

**Goals:**

- A collection that completes for a module of several hundred source files.
- The report of a module unchanged, documentation included.

**Non-Goals:**

- A faster parse of the module itself. Its cost is linear in the sources and is not what stalls.
- A change of the report format, of the type solver hierarchy, or of which solver resolves what.
- A timeout budget per module beyond the existing behaviour of a skipped module.

## Decisions

### The solver parses without comment attribution, the module parse keeps it

A symbol resolution needs the declarations of a type, never its comments. The solver therefore gets a parser
configuration of its own with the comment attribution switched off, and the module parse keeps the configuration it
uses today, so the documentation of a class and of a method - and every metric that counts it - is untouched.

*Alternative considered:* switch the comment attribution off globally. Rejected: the documentation ratio and the
documentation of a class come from that attribution, and dropping it would silently change a metric.

*Alternative considered:* replace the source based solver with a solver over the compiled artefacts only. Rejected:
the collection runs on projects that ship no compiled artefacts, and the source solver is what makes the resolution
work for them.

### The two parsers keep separate configurations

The module parse configures the static parser, and the solver builds its own configuration when it is constructed. The
two are not shared, so a later change of one cannot silently change the other.

## Risks / Trade-offs

- **A resolution that relied on a comment fails.** → No resolution path reads a comment; the tests of the metrics and
  the collected report of a module are the check.
- **The solver's configuration drifts from the module parse's.** → Both name the language level explicitly, and a
  test compares the report of a module collected through the two paths.
