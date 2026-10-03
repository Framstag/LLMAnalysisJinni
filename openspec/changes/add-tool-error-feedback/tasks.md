# Tasks

## 1. Failure reason and configuration

- [x] 1.1 Add the failure reason for an exceeded tool round bound to `lc4j/StepFailureReason.java` with its label, and verify with a unit test in `lc4j/TaskStepFailureTest.java` that `displayMessage()` and `repairHint()` name the bound and stay within the existing length bounds
- [x] 1.2 Add `maxToolRoundTrips` to `config/Config.java` (field, default 10, getter/setter, `dumpToLog()` row) and verify with a unit test that a config constructed without the key reports 10
- [x] 1.3 Add `maxToolRoundTrips` with `minimum: 1` to `src/main/resources/schema/config-file.json` and verify with a unit test that `config.json` carrying 0 is rejected as a structure error while a higher value loads and is reported as provided in the file
- [x] 1.4 Add the `maxToolRoundTrips` row to the configuration table in `README.md` and verify the documented default and minimum match `Config` and the config schema

## 2. Tool error policy wiring

- [x] 2.1 Wire `tools/ToolServiceFactory.java` to set the arguments handler to `ToolArgumentsErrorHandler.sendExceptionMessageToLlm()`, keep the execution handler returning the error text, and set the round-trip bound from `config.getMaxToolRoundTrips()`; verify with a unit test in `tools/ToolServiceFactoryTest.java` that the built service reports those handlers and that bound
- [x] 2.2 Replace `ChatExecutor`'s private handler and bound copies with values read from `executionContext.getToolService()`, and verify with a unit test that a `ToolService` configured with a text-returning arguments handler produces a tool result instead of a step failure
- [x] 2.3 Try `ToolService.executeWithErrorHandling(...)` in place of the private copy in `ChatExecutor`; if it compiles and the existing outcome tests pass unchanged, keep it, otherwise keep the private copy — and record which of the two applies in `design.md`

## 3. Recoverable tool errors

- [x] 3.1 Verify with a test that a tool call carrying a scalar for an array-typed parameter produces a tool result holding the argument error, that the tool was not executed, and that the step continues with a further model request (canned model plus a tool with a `List<String>` parameter, following `lc4j/ChatExecutorOutcomeTest.java`)
- [x] 3.2 Verify with a test that the corrected call then executes the tool and the accepted payload is returned, so the recovery ends in a normal step outcome
- [x] 3.3 Replace the hallucinated-tool-name throw with a bounded tool result naming the requested tool and the available tool names, and verify with a test that no tool runs and the step continues
- [x] 3.4 Emit one WARN engine log record when a tool error is returned to the model, naming the tool and that the model was asked to correct the call, and verify with a test that the record carries the task identifier and the tool name
- [x] 3.5 Update the `AGENTS.md` sentences that state tool errors fail a step immediately, and verify each remaining sentence against the tests of this group

## 4. Round-trip bound

- [x] 4.1 Count the tool rounds of one attempt in `executeMessages` against the bound read from the `ToolService`, and verify with a test that a model requesting exactly the bound is executed and not rejected
- [x] 4.2 Refuse the round beyond the bound and return a rejected outcome carrying the new reason, and verify with a test that the tools of the refused round were not executed and the outcome is rejected
- [x] 4.3 Write the chat log before returning that rejection, and verify with a test that the log file of the runaway attempt exists and holds the refused tool call
- [x] 4.4 Verify the rejected outcome flows through `TaskStepRetrier`: the next attempt is told the reason, and an exhausted budget marks the task failed without writing a response property
- [x] 4.5 Extend the `AGENTS.md` update with the `maxToolRoundTrips` option and the bound rejection, and verify the wording against the tests of this group

## 5. Integration verification

- [x] 5.1 Run `mvn verify` and confirm the unit tests, the SBOM tests and `smoke/JarSmokeIT` pass
- [x] 5.2 Run a live `analyse` against a workspace whose backend emits a scalar for a single-element array argument, and confirm the step recovers and completes; if the live case cannot be reproduced, record that in this change and name the canned-model test as the evidence
- [x] 5.3 Confirm with `state dump` and `logs/` that a step rejected for exceeding the bound is failed for the next run, that the rejection is reported, and that the attempt's chat log survived
- [x] 5.4 Run `openspec validate add-tool-error-feedback --strict` and confirm the change validates
