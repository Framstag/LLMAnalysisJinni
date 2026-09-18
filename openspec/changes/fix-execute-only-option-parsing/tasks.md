## 1. Option declaration

- [x] 1.1 Change the `-o` / `--executeOnly` declaration on `AnalyseCmd` to a single-value, repeatable option and verify the module compiles with `mvn -q compile`.
- [x] 1.2 Confirm the option description in the `analyse` help output describes the accepted forms, and verify the generated usage line no longer shows the option as taking an open-ended list.

## 2. Parser-level tests

- [x] 2.1 Add a unit test that parses `analyse -o TaskId <workspace>` and verify both the selection and the workspace directory argument are recognised.
- [x] 2.2 Add a unit test that parses `analyse <workspace> -o TaskId` and verify the same outcome, so ordering does not matter.
- [x] 2.3 Add unit tests for the list forms: `-o First,Second` selects both ids, `-o First -o Second` selects both ids, a single `-o First` selects only that id, and omitting the option selects nothing.
- [x] 2.4 Add a unit test that omits the workspace directory argument and verify the failure message names the missing workspace directory argument.
- [x] 2.5 Run `mvn -q test -Dtest=AnalyseCmdOptionParsingTest` and confirm all parsing tests pass.

## 3. Documentation

- [x] 3.1 Correct the usage example in `AGENTS.md` so it reflects the accepted option and workspace directory order.
- [x] 3.2 Correct the `-o` / `--executeOnly` description in `README.md` so it states the accepted forms and notes that ids are not accepted as separate space-separated arguments.
- [x] 3.3 Verify each documented invocation form against the built command, and confirm the documented forms and the observed behaviour match.

## 4. Verification

- [x] 4.1 Re-run the four invocations recorded in `design.md` under Context and confirm the two formerly failing forms now load the workspace configuration while the other two keep working.
- [x] 4.2 Run `mvn verify` and confirm the full test suite passes.
- [x] 4.3 Run `openspec validate fix-execute-only-option-parsing --strict` and confirm the change still validates against the final implementation.
