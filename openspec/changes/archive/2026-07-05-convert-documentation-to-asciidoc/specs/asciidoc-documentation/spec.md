## ADDED Requirements

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

#### Scenario: Test checks Asciidoc table syntax

- **WHEN** the test renders the template with sample data
- **THEN** it asserts presence of `|===` table delimiters

#### Scenario: Test checks Asciidoc heading syntax

- **WHEN** the test renders the template with sample data
- **THEN** it asserts presence of `=== ` section headings
