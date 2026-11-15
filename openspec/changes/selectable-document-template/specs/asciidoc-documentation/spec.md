# Spec Delta

## MODIFIED Requirements

### Requirement: Documentation template uses Asciidoc syntax

The system SHALL use an Asciidoc Handlebars template for documentation generation instead of the Markdown template.
The template is `Documentation.adoc.hbs` unless the `document` command is given a template name, in which case the
named template SHALL be used and SHALL use Asciidoc syntax as well.

#### Scenario: Template file exists

- **WHEN** the `document` command runs without a template name
- **THEN** it compiles `Documentation.adoc.hbs` from the documentation directory of the analysis directory

#### Scenario: Named template is compiled

- **WHEN** the `document` command runs with a template name
- **THEN** it compiles that template from the documentation directory of the analysis directory

#### Scenario: Template uses Asciidoc headings

- **WHEN** the template contains section headings
- **THEN** they SHALL use Asciidoc `=` / `==` / `===` / `====` syntax

#### Scenario: Template uses Asciidoc tables

- **WHEN** the template renders tabular data
- **THEN** it SHALL use `|===` delimited table syntax