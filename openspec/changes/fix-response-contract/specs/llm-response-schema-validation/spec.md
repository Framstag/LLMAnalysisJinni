# Spec Delta

## ADDED Requirements

### Requirement: A violation report identifies where and what was rejected

Every reported schema violation SHALL identify the position of the offending value inside the payload
and the offending value itself, or state that a required value is missing. An enumeration violation
SHALL name the permitted values. The report SHALL NOT depend on the default locale of the machine the
engine runs on, so that the same payload yields the same report everywhere.

#### Scenario: Enumeration violation
- **WHEN** a payload contains an enumerated property whose value is not one of the permitted values
- **THEN** the report SHALL name the position of that property in the payload
- **AND** the report SHALL name the rejected value
- **AND** the report SHALL name the permitted values

#### Scenario: Missing required property
- **WHEN** a payload lacks a property the schema requires
- **THEN** the report SHALL name the property
- **AND** the report SHALL name the position of the object that lacks it

#### Scenario: Locale independence
- **WHEN** the same payload is validated on a machine whose default locale is German and on one whose
  default locale is English
- **THEN** the two reports SHALL be equivalent

#### Scenario: The report stays visible during the run
- **WHEN** a response is rejected for schema violations
- **THEN** each violation SHALL be reported through the engine's logging path
- **AND** the report SHALL NOT be written directly to the terminal

### Requirement: The repair hint is bounded

The violations carried into the next attempt of the same step SHALL be bounded in number and in
characters. When the bound carries less than every violation, the hint SHALL state the total number of
violations, so that the model can tell a single wrong field from a response of the wrong shape.

#### Scenario: Many violations
- **WHEN** a response violates the schema in more places than the hint carries
- **THEN** the hint SHALL carry at most the bound
- **AND** the hint SHALL state how many violations the response had in total

#### Scenario: Long violation text
- **WHEN** a violation's text is longer than the bound allows
- **THEN** the hint SHALL stay within its character bound
- **AND** the part it carries SHALL remain readable

#### Scenario: The complete list remains available
- **WHEN** a response is rejected for schema violations
- **THEN** the complete violation list SHALL be available for the diagnosis outside the prompt

#### Scenario: Few violations are carried completely
- **WHEN** a response has fewer violations than the bound carries
- **THEN** the hint SHALL carry all of them
