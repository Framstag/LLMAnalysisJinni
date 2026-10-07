# Spec Delta

## Purpose

Defines how the engine's tool-call loop inside one task step answers tool calls it cannot carry out: which tool errors are returned to the model as a tool result, how a call for a tool that does not exist is answered, and how many tool-call rounds one attempt may spend.

## ADDED Requirements

### Requirement: A tool argument error is returned to the model

When the arguments of a tool call cannot be prepared for execution, the engine SHALL NOT end the step. It SHALL return a tool result that carries the error text, SHALL NOT execute the tool, and SHALL continue the step with a further model request so the model can correct the call.

#### Scenario: Argument cannot be coerced

- **WHEN** a tool call carries an argument that cannot be converted to the type the tool declares
- **THEN** the tool SHALL NOT be executed
- **AND** the model SHALL receive a tool result whose text is the argument error
- **AND** the step SHALL continue with a further model request
- **AND** the tool result SHALL appear in the chat log of that attempt

#### Scenario: Model corrects the call

- **WHEN** a tool call failed on its arguments and the model then calls the same tool with usable arguments
- **THEN** the tool SHALL be executed with those arguments
- **AND** the step SHALL continue normally

### Requirement: A tool execution error is returned to the model

When a tool raises while it is executed, the engine SHALL NOT end the step. It SHALL return a tool result that carries the error text and SHALL continue the step with a further model request.

#### Scenario: Tool raises

- **WHEN** an executed tool raises an error
- **THEN** the model SHALL receive a tool result that carries the error text
- **AND** the step SHALL continue with a further model request

### Requirement: A call for a tool that does not exist is answered

When the model requests a tool whose name no registered tool provides, the engine SHALL NOT end the step. It SHALL return a tool result that names the requested tool and the tools that exist, and the step SHALL continue with a further model request.

#### Scenario: Unknown tool name

- **WHEN** the model requests a tool name that does not exist
- **THEN** no tool SHALL be executed
- **AND** the model SHALL receive a tool result that names the requested name and the available tool names
- **AND** the step SHALL continue with a further model request

### Requirement: Tool rounds of one attempt are bounded

Within one step attempt the engine SHALL bound the number of tool-call rounds the model may request. When that bound is reached, the engine SHALL stop requesting tool executions for that attempt.

#### Scenario: Model stays within the bound

- **WHEN** a model requests tool rounds not exceeding the bound
- **THEN** every requested round SHALL be executed and answered as usual
- **AND** the step SHALL not be affected by the bound

#### Scenario: Round beyond the bound

- **WHEN** a model requests a further tool round after the bound is reached
- **THEN** the engine SHALL not execute the tools of that round

### Requirement: The tool round bound is configurable

The bound SHALL be read from the workspace configuration as `maxToolRoundTrips`, an integer with a default of 10 and a minimum of 1. It SHALL be settable in `config.json` and SHALL have no command line option.

#### Scenario: Default applies

- **WHEN** `config.json` does not set `maxToolRoundTrips`
- **THEN** the bound SHALL be 10

#### Scenario: Configured value applies

- **WHEN** `config.json` sets `maxToolRoundTrips` to a value of at least 1
- **THEN** the bound SHALL be that value

#### Scenario: Value below the minimum

- **WHEN** `config.json` sets `maxToolRoundTrips` to a value below 1
- **THEN** loading the workspace configuration SHALL fail as a configuration error

### Requirement: Exceeding the tool round bound rejects the step

A step attempt that reaches the tool round bound without an acceptable answer SHALL end as a rejected step attempt, with a failure reason that names the bound. The attempt SHALL store no response property, and the step SHALL be treated like any other rejected attempt of that step.

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

### Requirement: Tool error recoveries are reported

The engine SHALL report a tool argument error or a tool execution error that was returned to the model, and SHALL report a step rejected for exceeding the tool round bound, naming the tool where one is involved and the reason for the rejection.

#### Scenario: Argument error reported

- **WHEN** an argument error is returned to the model as a tool result
- **THEN** the engine SHALL record a report that names the tool and that the model was asked to correct the call

#### Scenario: Bound rejection reported

- **WHEN** a step attempt is rejected for exceeding the tool round bound
- **THEN** the engine SHALL record a report that names the bound
- **AND** the rejection SHALL be reported through the channels that report rejected attempts
