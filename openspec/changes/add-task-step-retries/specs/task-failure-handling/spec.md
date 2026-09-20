# Spec Delta

## MODIFIED Requirements

### Requirement: A failed task is retried on the next run

A task marked failed SHALL be treated as not yet completed when the analysis is run again, and a task marked successful SHALL be skipped. A task SHALL be marked failed only after the attempts it is allowed within the current run have been used, or after a failure that does not permit another attempt.

#### Scenario: Failed task runs again
- **WHEN** an analysis is run again after a task failed
- **THEN** that task SHALL be executed again

#### Scenario: Successful task is skipped
- **WHEN** an analysis is run again after a task succeeded
- **THEN** that task SHALL NOT be executed again

#### Scenario: A retryable failure is exhausted inside the run first
- **WHEN** a task step fails in a way that permits another attempt
- **AND** the attempts allowed for the run are not yet used
- **THEN** the task SHALL NOT yet be marked failed
- **AND** the step SHALL be attempted again in the same run

## ADDED Requirements

### Requirement: A non-conformant response fails the task once its attempts are used

A task step whose attempts were all rejected SHALL be marked failed, including when the last attempt produced a payload that parses but violates the declared response schema. The response property of that step SHALL NOT be written to the analysis results.

#### Scenario: Non-loop task with non-conformant attempts
- **WHEN** every attempt of a non-loop task produced a response that does not conform to the declared schema
- **THEN** the task SHALL be marked failed
- **AND** no response property SHALL be written for that task

#### Scenario: Loop task with a non-conformant index
- **WHEN** a loop task has at least one index whose attempts all produced a response that does not conform to the declared schema
- **THEN** the task SHALL be marked failed
- **AND** the indices that produced a conformant response SHALL stay recorded as successful

## REMOVED Requirements

### Requirement: A schema violation is not a failure

**Reason**: A non-conformant response is the case the in-run retry exists to correct. Accepting it as a success publishes a payload that does not match the declared contract, which every dependent task then reads, and it hides the violation behind a success status.
**Migration**: A non-conformant response is now a retryable failure. The task is attempted again, and it is marked failed once its attempts are used, without writing the response property. The replacement requirement is "A non-conformant response fails the task once its attempts are used" in this capability, and the retry behaviour itself lives in `llm-response-retry`.
