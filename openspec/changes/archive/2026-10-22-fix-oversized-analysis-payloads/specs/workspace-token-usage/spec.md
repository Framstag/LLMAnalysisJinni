# Spec Delta

## MODIFIED Requirements

### Requirement: The usage of a step that failed is kept

The system SHALL keep the recorded token usage of a step whose attempts were all rejected, because
the tokens of that step were spent.

The system SHALL also keep the recorded token usage of a step whose model call failed for a reason
another attempt cannot repair, because the calls that succeeded before that failure were paid for.

#### Scenario: A failed task keeps its usage

- **WHEN** a task fails after all its attempts were used
- **THEN** the execution state entry of that task carries the token usage of those attempts

#### Scenario: A failed loop index keeps its usage

- **WHEN** a loop index fails after all its attempts were used
- **THEN** the recorded usage of that index is kept

#### Scenario: A step whose model call failed keeps the usage of the calls that succeeded

- **WHEN** a model call of a step fails for a reason another attempt cannot repair
- **THEN** the recorded usage of that step carries the usage of the calls that succeeded before the failure

#### Scenario: A step that failed without spending a token carries no usage

- **WHEN** the first model call of a step fails
- **THEN** the recorded usage of that step is empty
