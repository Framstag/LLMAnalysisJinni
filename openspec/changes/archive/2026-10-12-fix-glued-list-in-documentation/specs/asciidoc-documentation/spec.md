# Spec Delta

## ADDED Requirements

### Requirement: A list is separated from the paragraph above it

The documentation template SHALL emit a blank line between a paragraph and a list it introduces, and SHALL
emit every list item on a line of its own without a blank line between the items.

A block whose body is a list SHALL NOT be introduced by a tag that trims the whitespace before it, because that
trim removes the blank line the list needs.

#### Scenario: The list of diagrams that were not drawn
- **WHEN** a module has diagrams the emission did not draw
- **THEN** the rendered document holds the paragraph `Not drawn:` followed by a blank line and one bullet per
  omission
- **AND** no bullet is on the paragraph line

#### Scenario: Tolerance of the renderer
- **WHEN** the rendered document is read as AsciiDoc
- **THEN** the markers are read as a list and not as text of the paragraph

## MODIFIED Requirements

### Requirement: Test verifies Asciidoc output

The documentation template test SHALL verify Asciidoc syntax in rendered output.

The verification SHALL cover the shape of a list and not only the presence of its text: no bullet emitted by
the template may be glued to the paragraph above it, because the renderer then prints the markers as text.

#### Scenario: Test checks Asciidoc table syntax
- **WHEN** the test renders the template with sample data
- **THEN** it asserts presence of `|===` table delimiters

#### Scenario: Test checks Asciidoc heading syntax
- **WHEN** the test renders the template with sample data
- **THEN** it asserts presence of `=== ` section headings

#### Scenario: Test checks list separation
- **WHEN** the test renders the template for a module that has diagrams the emission did not draw
- **THEN** it asserts a blank line between the paragraph and the first bullet, and one bullet per omission

#### Scenario: Test checks every bullet of the report
- **WHEN** the test renders the template with sample data
- **THEN** every bullet of the rendered report either opens a list or continues one
