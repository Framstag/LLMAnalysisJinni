# Proposal

## Why

Collecting the raw Java module report of a large module does not complete. The report collection parses the sources of
the module, and its symbol solver parses source trees of its own on every lookup, with the comment attribution of a
full parse. On a module of several hundred source files that path runs for more than twenty minutes without producing
a report, so a workspace whose reports have to be regenerated cannot be regenerated at all, and every analysis that
needs a report of the current format is blocked on it.

## What Changes

- Let the symbol solver of the report collection parse the sources it needs without the comment attribution, because
  resolving a symbol does not need a comment, while the parse of the module itself keeps its comment attribution, so
  the documentation a metric counts is unchanged.
- Report the collection of a module that does not complete as a skipped module with its reason, so that one module
  cannot stall the collection of all of them.
- Keep every report of the module identical to the report of the same module collected before this change.

## Capabilities

### Modified Capabilities

- `batch-java-report-collection`: collecting the raw report of a module completes for a module with a large source
  tree, and a module whose collection does not complete is reported as skipped with the reason instead of stalling
  the collection of all modules.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/JavaTool.java`: the type solver is created with a parser configuration
  of its own, and the module parse keeps the configuration it uses today.
- No report format change, and no change to the result of an existing metric: the class model, the references and the
  documentation counts are read from the parse of the module, which this change does not touch.
