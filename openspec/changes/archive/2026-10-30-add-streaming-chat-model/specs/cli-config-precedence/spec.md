# cli-config-precedence (delta)

## MODIFIED Requirements

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
- **THEN** at least the request logging, response logging, console execution trace, system message trace, task parallelism, and streaming settings SHALL follow the precedence order
