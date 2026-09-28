# Tasks

## 1. Configuration

- [x] 1.1 Add an `int retries` field to `config/Config.java` with default 3, getter/setter and a row in `dumpToLog()`, and verify with a unit test that a config constructed without the key reports 3
- [x] 1.2 Reject a configured value below 1 as a configuration error at load time in `config/ConfigLoader.java`, and verify with a unit test that `retries: 0` is reported instead of clamped
- [x] 1.3 Verify `config/ConfigStorer.java` writes the value and round-trips it, adjusting `config/ConfigStorerTest.java` if the serialized shape changes
- [x] 1.4 Add the `retries` row to the configuration table in `README.md`
- [x] 1.5 Confirm `config/ConfigOverrides.java` is untouched (no CLI option) and `config/ConfigOverridesTest.java` still passes

## 2. Step outcome and response validation

- [x] 2.1 Introduce the step outcome type and the failure reason enum (`NO_RESPONSE`, `NO_PAYLOAD`, `PAYLOAD_NOT_PARSEABLE`, `SCHEMA_VIOLATION`, `RETRIABLE_MODEL_ERROR`) carrying a bounded message, the violation messages and the parser excerpt, and verify with a unit test that each reason retains its payload
- [x] 2.2 Change `lc4j/ChatExecutor.executeMessages()` to return the step outcome instead of throwing for a rejected response, keeping `throws IOException` for genuine engine failures, and verify with a unit test that a blank response, a payload-free response, an unparseable payload and a schema violation each yield the matching reason
- [x] 2.3 Replace the warn-and-return schema validation block with a report of the violation messages through the outcome, and verify with a unit test that a violation produces `SCHEMA_VIOLATION` with the violation messages and no accepted payload
- [x] 2.4 Compose the repair hint in the user-message patch path so it follows the schema description, bounded in violation count and characters, and verify with a unit test that a hint reaches the patched user message after the schema description and that no hint is added for the first attempt
- [x] 2.5 Verify an accepted response still reaches the caller unchanged, so storage and the existing payload tests in `json/ResponsePayloadParserTest.java` stay green

## 3. Retry policy

- [x] 3.1 Add the retry policy type in `lc4j` that runs one step's attempts up to the configured budget and returns the outcome of the deciding attempt, and verify with a unit test that a step failing twice with a budget of 3 runs exactly 3 attempts
- [x] 3.2 Map `RetriableException` to a further attempt and the `NonRetriableException` subtree, `HttpException`, `UnsupportedFeatureException`, `ToolExecutionException` and `IOException` to an immediate failure, and verify with a unit test per branch that no extra attempt is made
- [x] 3.3 Pass the reason of the rejected attempt into the next attempt as the repair hint, and verify with a unit test that attempt 2 receives the violation messages of attempt 1
- [x] 3.4 Verify attempts are sequential and use no new thread pool, and that the policy is usable from a loop worker and from a plain task runner without shared mutable state

## 4. Analysis command integration

- [x] 4.1 Route the non-loop task call site in `AnalyseCmd.buildTaskRunner` through the retry policy, and verify that an accepted outcome stores the response property and marks the task successful, while an exhausted one marks the task failed and writes nothing
- [x] 4.2 Route the loop index worker through the retry policy, keeping `markIndexSuccessful` per accepted index and marking the task failed when any index is exhausted, and verify with a test that a retried index that succeeds is recorded as successful
- [x] 4.3 Verify a non-conformant but parsable payload is not written to the analysis results while it stays in the chat log
- [x] 4.4 Verify the fatal-failure path keeps its current behaviour: the task is marked failed, dependents stay blocked, and the next run executes the task again

## 5. Retry hints in the display and the logs

- [x] 5.1 Add `onRetry(taskId, loopIndex, attempt, maxAttempts, reason)` to `display/ProgressCallback.java` with a no-op default, and verify `ProgressCallback.noOp()` and all implementations still compile
- [x] 5.2 Show the current attempt against the budget on the TUI task and worker rows by adding attempt fields to `display/TaskRow.java` and `display/LoopWorkerRow.java`, and verify with unit tests on those row types
- [x] 5.3 Emit one WARN record per retry from the retry policy with the task identifier, the attempt and the bounded reason, and verify with a test that the record carries the task identifier so the TUI reserved line and `logs/engine.log` show it
- [x] 5.4 Print one retry line in `display/SimpleOutput.java` naming the step, the attempt and the reason, and verify with a test that a retried step produces exactly one line per retry
- [x] 5.5 Verify the reporting works in all three display modes (TUI, piped output, no-op under `--execution-trace`)

## 6. Per-attempt chat logs

- [x] 6.1 Add the attempt number to `lc4j/ChatLogger.writeLogFile()`, writing attempt 1 to the existing name and further attempts to `<taskId>[_<loopIndex>].attempt<N>.log`, and verify with a unit test for both names including a loop index
- [x] 6.2 Verify a rejected attempt's log file survives the next attempt of the same step and that a later run of the same step overwrites its attempt files
- [x] 6.3 Update `lc4j/ChatExecutionLoggingTest.java` for the new naming and confirm the file log still contains every message with its label and the token usage

## 7. Verification

- [x] 7.1 Run `mvn verify` and confirm the full suite, including the jar smoke test, passes
- [x] 7.2 Run a manual analysis against a workspace whose model is pointed at a broken endpoint to confirm a retriable error is retried and reported, and a workspace with a wrong API key to confirm a fatal error is not retried
- [x] 7.3 Confirm the run output and `logs/` show the attempt count for a deliberately non-conformant task, and that `state dump` reports the task as failed afterwards
- [x] 7.4 Run `openspec validate add-task-step-retries --strict` and confirm the change validates
