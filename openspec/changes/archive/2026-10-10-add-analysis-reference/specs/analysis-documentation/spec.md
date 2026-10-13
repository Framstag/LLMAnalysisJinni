# Spec Delta

## ADDED Requirements

### Requirement: Analysis README points to the analysis reference

The analysis README SHALL point to the reference document as the place that explains what an analysis measures
and how its result is to be read, instead of explaining an analysis in its own words.

#### Scenario: Pointer is present

- **WHEN** the analysis README is read
- **THEN** it names the reference document and states that it holds the description and the interpretation of
  the analyses

#### Scenario: No interpretation rule is restated

- **WHEN** the analysis README is searched for a threshold or a metric interpretation
- **THEN** it holds neither, only the pointer to the reference

## MODIFIED Requirements

### Requirement: Analysis README directory listing matches the directory

The analysis README SHALL list the files and subdirectories of the analysis directory as they are, so that a
reader neither looks for a file that does not exist nor misses one that does.

#### Scenario: Named files exist

- **WHEN** the directory structure section is read
- **THEN** every file it names by name exists in the analysis directory

#### Scenario: A renamed file is listed under its current name

- **WHEN** a file of the analysis directory is renamed
- **THEN** the directory structure section names it under its current name, for example the documentation
  template as `Documentation.adoc.hbs`

#### Scenario: The reference document is listed

- **WHEN** the directory structure section is read
- **THEN** it names the reference document of the analysis beside the documentation template

## REMOVED Requirements

### Requirement: Analysis README documents the dependency diagram levels

**Reason**: The reference document explains the dependency diagrams, together with every other analysis the
report renders. Keeping the same explanation in the README as well would state each diagram fact twice.

**Migration**: The statements are carried by the dependency diagram entry of the reference document, which the
README points to.

### Requirement: Analysis README documents that diagram omissions are stated

**Reason**: The reference document explains the dependency diagrams, together with every other analysis the
report renders. Keeping the same explanation in the README as well would state each diagram fact twice.

**Migration**: The statements are carried by the dependency diagram entry of the reference document, which the
README points to.

### Requirement: Analysis README documents the diagram budgets

**Reason**: The node, edge and diagram budgets belong with the analysis they bound. They move into the
steering table of the dependency diagram entry, where a reader of the report finds them as well.

**Migration**: The budget properties, their seeded values and what each one bounds are carried by the steering
table of the dependency diagram entry of the reference document.

### Requirement: Analysis README documents the god class definition

**Reason**: The reference document explains the god class ranking, together with every other analysis the
report renders. Keeping the factor list and the percentile rule in the README as well would state each of them
twice.

**Migration**: The factors and the percentile rule are carried by the god class entry of the reference
document, which the README points to.

### Requirement: Analysis README documents the god class ranking limits

**Reason**: The ranking limits belong with the analysis they bound. They move into the steering table of the
god class entry, where a reader of the report finds them as well.

**Migration**: The ranking limit properties, their seeded values and what each one bounds are carried by the
steering table of the god class entry of the reference document.
