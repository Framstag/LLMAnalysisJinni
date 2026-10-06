# Tasks

## 1. Loop cursor ownership

- [ ] 1.1 Replace the shared `loopPos` field in `state/StateManager.java` with a per-execution cursor value that holds the loop target array, and add the factory that creates it from the analysis state, and verify with a unit test that two cursors over the same array can be created without either creation being refused
- [ ] 1.2 Report a `loopOn` target that does not exist or is not an array as a failure of the creating execution only, and verify with a unit test that the failure message names the path and that a cursor over a valid array is still creatable afterwards
- [ ] 1.3 Move `getLoopArraySize`, `loopAtIndex` and `endLoop` onto the cursor value and delete the "Loop already started" state, and verify with a unit test that a cursor can be released and re-created and that no code path reports "already started"
- [ ] 1.4 Keep `updateLoopState` as a write through the cursor under the existing `StateManager` monitor, and verify with a unit test that two threads writing different properties of the same array entry both survive
- [ ] 1.5 Verify `state/StateManagerTest.java` (or its equivalent) covers the cursor contract, and adjust the existing loop tests that assert the global cursor

## 2. Unstartable execution fails its task

- [ ] 2.1 Mark the task failed in `cli/AnalyseCmd.java` when the loop cannot start, and verify with a unit test (or an integration test over a task list) that the task is FAILED, that no response property is written and that its tags do not unlock a dependent task
- [ ] 2.2 Report the unstartable execution once for that task through the existing error path, and verify with a test that exactly one message per task is emitted
- [ ] 2.3 Verify the retry-on-next-run rule still holds for such a task, by clearing and re-running the state in a test workspace

## 3. Dispatch rule

- [ ] 3.1 Record a task that ends without a status change as rejected for the run in the dispatch loop of `cli/AnalyseCmd.java`, and verify with a test that such a task is dispatched exactly once
- [ ] 3.2 Make the run end when the remaining pending tasks have unsatisfied dependencies, and verify with a test that the blocked tasks are named and that the run terminates instead of spinning
- [ ] 3.3 Verify that a normal run dispatches every task exactly once per run, and that a task whose completion unlocks a dependent still dispatches that dependent in the same round

## 4. Bounded engine log

- [ ] 4.1 Replace the plain file appender in `logging/EngineLogRouting.java` with a size-bounded rolling appender (maximum file size and bound on the total size of the engine log files), and verify with a unit test that a run emitting more records than the bound leaves a workspace whose engine log files together stay within the bound
- [ ] 4.2 Verify the newest records are kept, by asserting the tail of `logs/engine.log` contains the last record of the run
- [ ] 4.3 Verify the non-TUI paths are untouched: `--execution-trace` and piped runs still keep the console appender and write no engine log file

## 5. Reserved TUI warning line

- [ ] 5.1 Skip the repaint in `display/ProgressDisplay.java` when the incoming record has the same text as the record currently shown, and verify with a unit test that no render happens for the same text and that a different text still renders
- [ ] 5.2 Verify the line still shows the newest record, including the case where the newest record repeats the shown one after an intervening different record

## 6. Verification

- [ ] 6.1 Run `mvn verify` and confirm the full suite, including the jar smoke test, passes
- [ ] 6.2 Run a copy of the `workspaces/maven` workspace (or a domain with several loop tasks) in TUI mode and confirm that no `Loop already started` record is written, that every loop task executes, and that `logs/engine.log` stays within the bound
- [ ] 6.3 Confirm a re-run skips the loop indices recorded as successful in the first run and executes only the remaining ones
- [ ] 6.4 Run `openspec validate fix-concurrent-loop-execution --strict` and confirm the change validates
