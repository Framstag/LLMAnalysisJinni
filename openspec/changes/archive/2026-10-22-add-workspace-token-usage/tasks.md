# Tasks

## 1. Carry the token usage of an attempt to the step

- [x] 1.1 Add the token usage of an attempt to the outcome of that attempt, so an outcome carries
      either a payload or a failure and, in both cases, the usage of the attempt, and verify with a
      unit test that an accepted and a rejected outcome each carry their usage
- [x] 1.2 Have the executor hand the usage of the attempt to the outcome it returns, and verify
      with a unit test that the usage of an attempt that made several model calls is the sum of
      those calls
- [x] 1.3 Have the retry driver sum the usages of the attempts of a step into the usage of that
      step, and verify with a unit test that a step attempted three times reports the sum of the
      three usages and that a step accepted on its first attempt reports that attempt's usage
- [x] 1.4 Verify that a step whose attempts were all rejected still reports the sum of their
      usages, with a unit test on the retry driver

## 2. Record the usage of every step in the execution state

- [x] 2.1 Add the usage of a task and the usage per loop index to the state entry of a task, with
      both absent until a step records a usage, and verify with a unit test that a state entry
      without a usage round-trips through the state file unchanged
- [x] 2.2 Have the task manager record the usage of a step: the task usage for a non-loop task, the
      index usage for a loop index, and the task usage of a loop task as the sum of the usages of its
      indices written from the same computation, and verify with a unit test that the task usage and
      the sum of the index usages agree
- [x] 2.3 Preserve the recorded usage in every place the state entry of a task is replaced, so
      marking a task successful, marking it failed and marking a loop index successful keep the
      usage that was recorded before, and verify with a unit test that covers each of the three
- [x] 2.4 Replace the usage of a step when the step runs again, and keep the usage of a step that is
      skipped, and verify with a unit test that a re-executed index has the new usage and a skipped
      index has the one of its last execution
- [x] 2.5 Have the analysis record the usage of every step it runs, for the non-loop task and for
      each loop index, and verify that a run leaves the usage of its steps in the state file

## 3. Report the token usage of a workspace

- [x] 3.1 Add a `state tokens` subcommand that reports the total input, output and token count over
      all tasks of the workspace, and the usage of each task that has one, and verify by running the
      command against a workspace whose state carries usages
- [x] 3.2 Have the report name the usage of each loop index of a loop task, and verify by running
      the command against a workspace whose state carries per-index usages
- [x] 3.3 Have the report state that no token usage is recorded when the workspace has none, and
      verify by running the command against a workspace whose state carries no usage
- [x] 3.4 Have the report write the same data as JSON when a JSON output is requested, and verify
      that the JSON holds the same totals, per-task usages and per-index usages as the table

## 4. Show the token usage of a task on its row

- [x] 4.1 Have the live display put the usage of a task on the row of that task, and the usage of a
      loop index on the row of that worker, and verify with a unit test that the rendered row of a
      task carries its input and output counts
- [x] 4.2 Verify that the count on the row of a task is the count the footer adds to its aggregate,
      with a unit test on the display
- [x] 4.3 Remove the token count class that no code uses and the display method that no code calls,
      and verify that the project still compiles and that no reference to them is left

## 5. Document and check the change

- [x] 5.1 Document the `state tokens` command and the token usage the execution state carries in
      the README, and verify that the documented command runs as written
- [x] 5.2 Run `mvn verify` and verify that the build, the unit tests and the artefact smoke test
      pass
- [x] 5.3 Verify that a state file written before this change still loads and that the report of
      such a workspace states that no token usage is recorded

## 6. Defects found while implementing

Three defects in the existing code were found while the token usage was wired through. They are
fixed here, because the change would otherwise have inherited them and made them visible.

- [x] 6.1 The footer total of the live display counted the running aggregate of a step once per
      response, so a step that made three model calls was counted more than twice. The display now
      adds only what a step has spent since its last report, and a further attempt of a step starts
      a new aggregate. Verified with a unit test on the tracker and one on the rendered footer.
- [x] 6.2 The token count on a task row was read as a primitive number, so a provider that reports
      no count for a kind of token made the render fail. A count that is not reported is now shown
      as a dash.
- [x] 6.3 The token counts were formatted with the locale of the machine, so a German locale
      rendered a count of 1234 as "1,2K". The formatting uses the root locale, like the other
      numbers the engine renders.

## 7. Defects found while running the analysis

Two more defects showed up in a real analysis of a workspace with 90 modules, and both are fixed.

- [x] 7.1 The report named the tasks and the loop indices in the order they were recorded, which
      is the order they finished in. A reader expects them ordered, so the tasks are ordered by
      their id and the indices by their index. Verified with a unit test on the report.
- [x] 7.2 The workspace configuration of the analysed workspace needed a higher tool round bound,
      because the cloud model asks for one file check per tool round and reached the bound of 10.
      The bound is a setting of the workspace, not a defect of the engine, so it was raised in the
      configuration of that workspace.
