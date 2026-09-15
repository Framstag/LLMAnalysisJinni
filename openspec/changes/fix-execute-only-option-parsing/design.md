## Context

See proposal.md - Why. The relevant constraint is how the option is declared today in `src/main/java/com/framstag/llmaj/cli/AnalyseCmd.java`:

```java
@Option(names={"-o","--executeOnly"}, arity = "1..*", description = "A list of task ids, that should only be executed")
Set<String> executeOnly = new HashSet<>();
```

`arity = "1..*"` lets the option accept an unbounded run of following arguments, and Picocli binds those before it binds the single positional `workingDirectory`. The workspace directory is that positional, so it is swallowed whenever `-o` comes first. Measured behaviour of the current declaration:

```
analyse -o A,B <ws>     -> Missing required parameter: '<workingDirectory>'
analyse -o A -o B <ws>  -> Missing required parameter: '<workingDirectory>'
analyse <ws> -o A       -> works
analyse <ws> -o A B     -> works, selects A and B
```

`state drop` also declares `arity = "1..*"`, but on a trailing positional parameter where an open-ended list is the intended contract, so it is not affected and stays as is.

## Goals / Non-Goals

**Goals:**

- The workspace directory binds correctly regardless of option order.
- Both documented list forms (comma-separated, repeated option) select the same set of tasks.
- A malformed invocation fails with a message that names the actual problem.

**Non-Goals:**

- Rejecting a selection that names no task at all. That id is silently ignored today; rejecting it is a separate behaviour change and a separate decision, not part of fixing the argument parsing.
- Changing how selected tasks are validated, ordered, or executed.
- Changing the `--single-step` or `--task-parallelism` options, or any `state` subcommand.

## Decisions

### Decision 1: `arity = "1"` on the option, with an explicit comma split

One value per occurrence, repeatable, with `split = ","` so a single occurrence can carry a list. Repeated occurrences accumulate into the same `Set<String>`. Because the option can no longer consume more than one argument, the positional binds to whatever is left, whatever the order.

The explicit `split` is required: Picocli's default `split` only applies to an option that accepts several values per occurrence, so with `arity = "1"` the value would otherwise be taken verbatim and `-o A,B` would select a task literally named `A,B`. That was confirmed by a failing unit test before the attribute was added.

Alternatives considered:

- *Keep `arity = "1..*"` and document that the workspace directory must come first.* Rejected: `AGENTS.md` documents the opposite order, and an ordering trap that produces a misleading "missing workspace directory" error is the defect being fixed.
- *Keep `arity = "1..*"`, add `split = ","`, and pass the positional through a reordering step.* Rejected: fighting the parser rather than declaring the intended arity.
- *Accept only repeated options (`-o A -o B`) and drop comma splitting.* Rejected: `README.md` documents the comma form.

Known consequence: `analyse <ws> -o A B` stops being accepted as two ids. With a single-value option the second value binds to the workspace directory positional, so the run fails while loading the configuration of a directory named `B`. Measured after the change: `analyse -o Welcome LocateREADME` reports `Cannot load config file`, having treated `LocateREADME` as the workspace directory.

### Decision 2: Pin the accepted forms with parser-level unit tests

The repository tests behaviour with plain JUnit and has no CLI integration harness, so the tests parse an `AnalyseCmd` with `CommandLine` and assert the resulting selection, following the existing `AnalyseCmdOptionParsingTest`. This keeps the contract from regressing without needing a running model.

## Risks / Trade-offs

- [Rejecting `-o A B` could break a local script that relies on it] → The form is undocumented; the run fails immediately while loading a configuration directory named after the second id, and the documented comma form is unaffected.
- [Picocli's comma splitting for a single-arity collection value depends on the explicit `split` attribute] → Verified during implementation: without it the value is taken verbatim, and the pinned unit test asserting `-o A,B` yields both ids fails, so a change in that behaviour cannot pass unnoticed.
- [The misleading "missing workspace directory" error message is what hid this defect for so long] → Covered by a scenario that asserts the message names the missing workspace directory argument, so the error stays accurate for genuinely missing arguments.

## Migration Plan

- No data migration and no configuration change.
- Deploy: single commit with the option declaration, the documentation examples, and the parsing tests.
- Rollback: revert the commit; the previous greedy behaviour returns.
- Verification: `mvn verify`, plus the four invocations listed under Context re-run to confirm the two formerly failing forms now work and the workspace directory binds.
