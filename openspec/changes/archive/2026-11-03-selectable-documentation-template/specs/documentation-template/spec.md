## ADDED Requirements

### Requirement: Document generation supports selecting an alternate template by name

The document command SHALL accept an optional template name identifying which `.hbs` template under the `documentation/` directory of the working directory to render, in addition to its existing required working-directory argument. The name is given without the `.hbs` suffix, and the selected template SHALL be rendered against the same analysis state and into the same output file as the default.

#### Scenario: Template name explicitly supplied

- **WHEN** the document command is invoked with a template name of `Documentation_German.adoc`
- **THEN** the document is rendered from `documentation/Documentation_German.adoc.hbs` using the same analysis state as without the option

#### Scenario: Template name omitted preserves existing behavior

- **WHEN** the document command is invoked without a template name
- **THEN** the document is rendered from `documentation/Documentation.adoc.hbs`, exactly as it was before this requirement existed
