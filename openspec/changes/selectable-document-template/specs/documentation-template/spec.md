# Spec Delta

## ADDED Requirements

### Requirement: Document generation renders a template selected by name

The `document` command SHALL accept an optional template name, given without the `.hbs` suffix, naming the template
under the `documentation/` directory of the analysis directory that the workspace's `config.json` names. The selected
template SHALL be rendered against the same analysis state and written to the same `Documentation<postfix>` file as
the default template.

#### Scenario: Template name supplied

- **WHEN** the document command is invoked with the template name `Documentation_German.adoc`
- **THEN** the document is rendered from `documentation/Documentation_German.adoc.hbs` of the configured analysis
  directory, against the same analysis state as without the option
- **AND** the result is written to `Documentation<postfix>` in the workspace, so the template name does not change
  the name of the output file

#### Scenario: Template name omitted

- **WHEN** the document command is invoked without a template name
- **THEN** the document is rendered from `documentation/Documentation.adoc.hbs`, exactly as it was before this
  requirement existed

#### Scenario: Template name matches no template

- **WHEN** the template name matches no `.hbs` file in the documentation directory
- **THEN** the command fails with the template loader's error and writes no document