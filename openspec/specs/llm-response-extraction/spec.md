# llm-response-extraction Specification

## Purpose

Defines how the JSON payload of a model response is located and normalised before it is parsed,
so that a task result is taken from the response content and not from the model's formatting
habits.

## Requirements

### Requirement: Payload is located independently of its position in the response

The system SHALL locate the JSON payload of a model response regardless of any text or code fence
surrounding it. Location SHALL NOT depend on the payload starting the response or ending it, and
SHALL NOT depend on the response being wrapped in a code fence. Brackets and quotes that appear
inside a JSON string, and escaped characters, SHALL be treated as string content and SHALL NOT be
taken as nesting or as the end of the payload.

#### Scenario: Response is only the payload
- **WHEN** a model response consists of the JSON payload alone
- **THEN** that payload SHALL be located unchanged

#### Scenario: Prose precedes the payload
- **WHEN** a model response contains an explanatory sentence, then a fenced `json` block with the payload
- **THEN** the payload alone SHALL be located

#### Scenario: Prose follows the payload
- **WHEN** a model response contains a fenced payload followed by an explanatory sentence
- **THEN** the payload alone SHALL be located, without the trailing text

#### Scenario: Brackets inside strings do not end the payload
- **WHEN** a located payload contains a string value with `{`, `}` or `[` characters, or with an escaped quote
- **THEN** the payload SHALL be located up to its real closing bracket

#### Scenario: Payload on a single line
- **WHEN** a model response contains the payload on one line, preceded or followed by text
- **THEN** the payload alone SHALL be located

#### Scenario: Trivial fragment in prose does not win
- **WHEN** a model response mentions an empty JSON fragment in prose before the real payload
- **THEN** the real payload SHALL be located, not the empty fragment

### Requirement: A located payload is normalised without acquiring new defects

The system SHALL remove code fence markers and surrounding whitespace, and SHALL repair
unbalanced quotes only within an already located payload. Normalisation SHALL NOT change a
response that already parses into a different document, and SHALL NOT leave the payload
unparseable when it parsed before normalisation.

#### Scenario: Response that parses is preserved
- **WHEN** a model response parses as JSON before normalisation
- **THEN** the result after normalisation SHALL parse to an equivalent document

#### Scenario: Unbalanced quotes inside a value are repaired
- **WHEN** a located payload contains a quoted value whose inner quotes are not escaped
- **THEN** the inner quotes SHALL be escaped and the payload SHALL parse

### Requirement: A response without a locatable payload is reported as such

When no payload can be located in a response, the system SHALL report that the response contains no
JSON payload, SHALL NOT report a repair that did not take place, and SHALL NOT write the complete
response body to the console for this condition. The result reported for the task SHALL name the
missing payload rather than a parser message about the first unrelated character.

#### Scenario: Response without any payload
- **WHEN** a model response contains prose only
- **THEN** the run SHALL report that no JSON payload was found in that response
- **AND** the report SHALL NOT claim that the response was corrected
- **AND** the complete response body SHALL NOT be written to the console

#### Scenario: Truncated payload
- **WHEN** a model response opens a code fence or an object but the payload is cut off before its closing bracket
- **THEN** the run SHALL report that no complete JSON payload was found
- **AND** the affected task SHALL NOT be treated as having produced a payload

#### Scenario: Failure is attributed to the missing payload
- **WHEN** a task's response contains no locatable payload
- **THEN** the failure reported for that task SHALL state that no JSON payload was found in the response

### Requirement: Missing payload and invalid payload are distinguished

The system SHALL distinguish a response in which no payload was located from a response in which a
payload was located but does not parse, and SHALL report the two conditions differently.

#### Scenario: Located payload does not parse
- **WHEN** a located payload is syntactically invalid JSON
- **THEN** the run SHALL report a parse failure for a located payload
- **AND** the affected task SHALL NOT be treated as having produced a payload

#### Scenario: No payload located
- **WHEN** a response contains no bracketed JSON payload at all
- **THEN** the run SHALL report the missing payload, not a parse failure of located content
