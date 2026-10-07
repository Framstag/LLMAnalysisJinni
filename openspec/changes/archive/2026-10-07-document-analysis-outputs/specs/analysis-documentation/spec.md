# Spec Delta

## ADDED Requirements

### Requirement: Analysis README documents the dependency diagram levels

The analysis README SHALL document what the dependency diagrams show: that each module gets a package-level overview and, in addition, class-level diagrams for its groups, and that a group's diagram draws the other groups collapsed and weighted with the total coupling to them.

#### Scenario: Both levels are described

- **WHEN** the dependency diagram section of the analysis README is read
- **THEN** it states that the package overview aggregates the couplings between the packages of a module and that the class detail diagrams show the groups of a module

#### Scenario: Group derivation is described

- **WHEN** the dependency diagram section is read
- **THEN** it states that the groups are the cheapest separation of the module's class reference graph and that groups left out of a class detail diagram are drawn collapsed and weighted with the coupling to them

### Requirement: Analysis README documents that diagram omissions are stated

The analysis README SHALL document that every emitted diagram states its own omissions in its caption and that a diagram which could not be drawn is reported with its reason rather than stored as broken source.

#### Scenario: Captions are described

- **WHEN** the dependency diagram section is read
- **THEN** it states that a caption names how many classes the diagram shows out of the total and how many edges were omitted below the minimum weight

#### Scenario: Undrawn diagrams are described

- **WHEN** the dependency diagram section is read
- **THEN** it states that a group above the node budget, or a module whose class name cannot be written into the diagram syntax, is reported with its reason instead of being drawn or stored broken

### Requirement: Analysis README documents the diagram budgets

The analysis README SHALL name the configuration properties that bound the emitted diagrams, state the default of each, and state what each one bounds, so a reader can tell what a changed configuration does to the diagrams of a run.

#### Scenario: Budget properties are named with defaults

- **WHEN** the configuration part of the dependency diagram section is read
- **THEN** it names each diagram property and the value the workspace configuration is seeded with

#### Scenario: Effect of each property is stated

- **WHEN** the configuration part of the dependency diagram section is read
- **THEN** each property is described by what it bounds, including the edge weight below which a reference is not drawn and the node count above which the exact minimum cut is skipped

### Requirement: Analysis README documents the god class definition

The analysis README SHALL document the factors the god class ranking scores and that the ranking is a percentile position within the module, not an absolute threshold and not a pass or fail verdict, and SHALL describe the cohesion factor as an approximation rather than as the exact metric.

#### Scenario: Ranking factors are listed

- **WHEN** the god class section of the analysis README is read
- **THEN** it names the factors the ranking scores, including the size factor and the foreign data access factor

#### Scenario: Percentile position is stated instead of a verdict

- **WHEN** the god class section is read
- **THEN** it states that every factor is a percentile within the module, that no absolute threshold decides the outcome, and that the ranking produces no pass or fail classification

#### Scenario: A large cohesive class is not a candidate

- **WHEN** the god class section is read
- **THEN** it states that a large class whose methods work on its own state is not a god class candidate, because its cohesion percentile is high

#### Scenario: The cohesion factor is not overclaimed

- **WHEN** the god class section is read
- **THEN** the cohesion factor is described as an approximation, not as the exact metric

### Requirement: Analysis README documents the god class ranking limits

The analysis README SHALL name the configuration properties that bound the god class ranking, state the default of each, and state what each one bounds.

#### Scenario: Ranking limits are named with defaults

- **WHEN** the god class section of the analysis README is read
- **THEN** it names the property bounding the ranking of one module and the property bounding the ranking reported per module when all modules are reported at once, with the default of each

#### Scenario: Effect of a changed limit is stated

- **WHEN** the god class section is read
- **THEN** each limit is described by what it bounds, so a reader can tell that raising it reports more ranked classes per module

### Requirement: Analysis README task table is a single table

The analysis README SHALL hold its task table as one Markdown table, with no blank line and no other content between two of its rows, so that it renders as a single table.

#### Scenario: No blank line splits the table

- **WHEN** the task table is rendered
- **THEN** every task row is contiguous with the next row and the table renders as one table

#### Scenario: A new task keeps the table intact

- **WHEN** a task row is added
- **THEN** it is appended as another row of the same table, without a separating blank line

### Requirement: Analysis README directory listing matches the directory

The analysis README SHALL list the files and subdirectories of the analysis directory as they are, so that a reader neither looks for a file that does not exist nor misses one that does.

#### Scenario: Named files exist

- **WHEN** the directory structure section is read
- **THEN** every file it names by name exists in the analysis directory

#### Scenario: A renamed file is listed under its current name

- **WHEN** a file of the analysis directory is renamed
- **THEN** the directory structure section names it under its current name, for example the documentation template as `Documentation.adoc.hbs`
