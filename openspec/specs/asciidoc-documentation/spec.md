# Asciidoc Documentation

## Purpose

Ensures the documentation generation pipeline produces Asciidoc (`.adoc`) output instead of Markdown. The Handlebars template uses Asciidoc syntax for headings, tables, code blocks, and blockquotes, and the CLI defaults to `.adoc` output postfix.

## Requirements

### Requirement: Documentation template uses Asciidoc syntax

The system SHALL use an Asciidoc Handlebars template (`Documentation.adoc.hbs`) for documentation generation instead of the Markdown template.

#### Scenario: Template file exists

- **WHEN** the `document` command runs
- **THEN** it compiles `Documentation.adoc.hbs` from the analysis documentation directory

#### Scenario: Template uses Asciidoc headings

- **WHEN** the template contains section headings
- **THEN** they SHALL use Asciidoc `=` / `==` / `===` / `====` syntax

#### Scenario: Template uses Asciidoc tables

- **WHEN** the template renders tabular data
- **THEN** it SHALL use Asciidoc `|===` delimited table syntax

### Requirement: CLI defaults to .adoc output

The `document` CLI command SHALL default `--document-postfix` to `.adoc`.

#### Scenario: Default output is .adoc

- **WHEN** user runs `document <workspace>` without `--document-postfix`
- **THEN** output file is `Documentation.adoc`

#### Scenario: Postfix is configurable

- **WHEN** user passes `--document-postfix .md`
- **THEN** output file uses the specified postfix

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
