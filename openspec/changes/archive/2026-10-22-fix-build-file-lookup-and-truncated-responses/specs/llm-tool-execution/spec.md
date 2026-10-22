# Spec Delta

## MODIFIED Requirements

### Requirement: Tool rounds of one attempt are bounded

Within one step attempt the engine SHALL bound the number of tool-call rounds the model may request. When that bound is reached, the engine SHALL stop requesting tool executions for that attempt. The bound on the rounds SHALL NOT bound the messages that carry the task, and the engine SHALL keep those messages for the whole attempt.

#### Scenario: Model stays within the bound
- **WHEN** a model requests tool rounds not exceeding the bound
- **THEN** every requested round SHALL be executed and answered as usual
- **AND** the step SHALL not be affected by the bound

#### Scenario: Round beyond the bound
- **WHEN** a model requests a further tool round after the bound is reached
- **THEN** the engine SHALL not execute the tools of that round

#### Scenario: A long tool conversation keeps the task
- **WHEN** a step needs more tool rounds than the window of the conversation holds messages for
- **THEN** the messages that carry the task SHALL still be part of every request the model is asked with
- **AND** the model SHALL NOT be left without the task it was given

#### Scenario: The window does not start with a result the model cannot place
- **WHEN** the conversation of an attempt is cut down to the messages that fit
- **THEN** the cut SHALL NOT leave a tool result as the first message
- **AND** every tool result in the conversation SHALL be preceded by the message that asked for it

### Requirement: Exceeding the tool round bound rejects the step

A step attempt that reaches the tool round bound without an acceptable answer SHALL end as a rejected step attempt, with a failure reason that names the bound. The attempt SHALL store no response property, and the step SHALL be treated like any other rejected attempt of that step. The reason SHALL name the tools the model asked for, so that a step that reached the bound because the model asked for too much is recognisable as such.

#### Scenario: Rejected and retried

- **WHEN** an attempt reaches the tool round bound
- **AND** further attempts of that step are available
- **THEN** the attempt SHALL be rejected with a reason naming the bound
- **AND** the next attempt SHALL start a fresh conversation and be told that reason

#### Scenario: Budget exhausted

- **WHEN** every attempt of a step reaches the tool round bound
- **THEN** the task SHALL be marked failed
- **AND** no response property SHALL be written for that step
- **AND** the tasks that depend on the failed task's tags SHALL not run in that run

#### Scenario: The reason names what the model asked for

- **WHEN** an attempt reaches the tool round bound
- **THEN** the reason reported for that attempt SHALL name the tools the model asked for
- **AND** it SHALL name how often the model asked for them

#### Scenario: A step that asked for one file after another is recognisable

- **WHEN** a step reached the bound by asking for the existence of one file after another
- **THEN** the reason reported for that attempt SHALL name the tool it asked for
