# Spec Delta

## MODIFIED Requirements

### Requirement: TUI shows per-task token usage

The system SHALL display input and output token counts per task, on the row of that task, and SHALL
add them to the aggregate it reports in the footer.

#### Scenario: Token usage shown on task row
- **WHEN** a task completes
- **THEN** the TUI SHALL show the input and output token count on that task's row

#### Scenario: Token usage of a loop index is shown on its worker row
- **WHEN** a loop index of a loop task reports token usage
- **THEN** the TUI SHALL show the input and output token count of that index on the row of that worker

#### Scenario: The task row and the footer agree
- **WHEN** a task reports token usage
- **THEN** the count shown on the row of that task SHALL be the count that task contributed to the aggregate of the footer

#### Scenario: A step that made several responses is counted once
- **WHEN** a task reports the running aggregate of its usage more than once
- **THEN** the aggregate of the footer SHALL count the usage of that task once
- **AND** it SHALL NOT count the running aggregate of that task once per report
