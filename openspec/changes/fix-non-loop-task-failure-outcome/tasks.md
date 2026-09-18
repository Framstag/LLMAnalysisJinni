## 1. Reproduce the defect

- [x] 1.1 Record the current behaviour with a stub model server that answers a chat request with an empty payload, and verify that the task ends up recorded as successful while the display reports a failure.
- [x] 1.2 Add a stub model server to the verification setup that returns a well-formed chat response with empty content, and verify the command line tool reaches it and reports `No response from chat model`.

## 2. Fix the outcome handling

- [x] 2.1 Derive the non-loop task status from the returned payload so a missing payload marks the task failed, and verify the module compiles with `mvn -q compile`.
- [x] 2.2 Confirm by code inspection that the display outcome and the recorded task status use the same condition, so they cannot disagree again.

## 3. Tests

- [x] 3.1 Add unit tests to `TaskManagerTest` asserting that a failed task does not add its tags to the scheduled tags and stays out of the successfully processed set, and verify they pass.
- [x] 3.2 Add a unit test to `TaskManagerTest` asserting that a task recorded as failed is pending again after state is reloaded while a successful task is not, and verify it passes.
- [x] 3.3 Run `mvn -q test -Dtest=TaskManagerTest` and confirm all task manager tests pass.

## 4. Verification

- [x] 4.1 Re-run the stub model scenario from task 1.1 and verify the task is recorded as failed, no response property is written for it, and `state.json` reports `FAILED`.
- [x] 4.2 Run the same scenario with a task and a dependent selected, and verify the dependent is not executed once the task it depends on fails.
- [x] 4.3 Re-run the same analysis a second time and verify the failed task is executed again while a successful task is skipped.
- [x] 4.4 Run `mvn verify` and confirm the full test suite passes.
- [x] 4.5 Run `openspec validate fix-non-loop-task-failure-outcome --strict` and confirm the change still validates against the final implementation.
