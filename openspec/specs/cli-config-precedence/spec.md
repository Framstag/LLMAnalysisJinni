# cli-config-precedence Specification

## Purpose

Defines how an explicitly passed CLI option, a value stored in the workspace `config.json`, and a built-in default combine into the effective configuration of a run, so that persisted workspace settings can be relied upon and are only overridden when a user asks for it on the command line.

## Requirements

### Requirement: Explicit CLI options take precedence over workspace configuration

The system SHALL resolve each overridable option using the order: value explicitly passed on the command line, then the value stored in the workspace `config.json`, then the built-in default.

#### Scenario: Explicit flag wins over config
- **WHEN** `config.json` stores `"logResponses": false`
- **AND** `analyse` is invoked with `--log-response true`
- **THEN** the effective value of the response logging option SHALL be `true`

#### Scenario: Config wins over the built-in default
- **WHEN** `config.json` stores `"logResponses": true`
- **AND** `analyse` is invoked without any response logging option
- **THEN** the effective value of the response logging option SHALL be `true`

#### Scenario: Built-in default applies when nothing else is set
- **WHEN** `config.json` does not contain a response logging entry
- **AND** `analyse` is invoked without any response logging option
- **THEN** the effective value of the response logging option SHALL be the built-in default

#### Scenario: Config value survives an unrelated run
- **WHEN** `config.json` stores `"taskParallelism": 8`
- **AND** `analyse` is invoked without the task parallelism option
- **THEN** the run SHALL use a task parallelism of 8

### Requirement: Overridable options distinguish absent from default-valued

Each option that may also be supplied through `config.json` SHALL be able to distinguish "the user did not pass this option" from "the user passed this option with a value equal to the default", so that not passing an option never writes into the configuration.

#### Scenario: Passing the default value explicitly overrides config
- **WHEN** `config.json` stores `"logRequests": true`
- **AND** `analyse` is invoked with `--log-request false`
- **THEN** the effective value of the request logging option SHALL be `false`

#### Scenario: Omitting an option leaves the configuration untouched
- **WHEN** `analyse` is invoked without an option for a given overridable setting
- **THEN** the loaded configuration value for that setting SHALL remain unchanged for the duration of the run

#### Scenario: Overridable option inventory
- **WHEN** the effective configuration is computed
- **THEN** at least the request logging, response logging, console execution trace, system message trace, and task parallelism settings SHALL follow the precedence order

### Requirement: Effective configuration is observable

The effective value of every overridable setting SHALL be reported in the run log after the merge, together with which source supplied it.

#### Scenario: Sources are reported
- **WHEN** a run starts
- **THEN** the log SHALL state the effective value of each overridable setting
- **AND** it SHALL be possible to tell from the log whether a value came from the command line, from `config.json`, or from the built-in default
