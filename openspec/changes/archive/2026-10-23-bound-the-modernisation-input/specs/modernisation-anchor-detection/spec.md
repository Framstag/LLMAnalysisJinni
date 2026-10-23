# Spec Delta

## ADDED Requirements

### Requirement: The anchor candidates are bounded by a budget

The system SHALL provide a tool that returns the anchor candidates of the project per anchor type as a bounded,
ranked list, SHALL bound the number of candidates it returns per type by a workspace property, and SHALL state beside
each bounded list how many candidates of that type the project holds.

#### Scenario: A project with more candidates than the budget

- **WHEN** the tool is called for a project whose seam candidates outnumber the budget of the candidate list
- **THEN** the result holds the strongest candidates up to the budget and states the total number of seam candidates
  of the project

#### Scenario: A project with fewer candidates than the budget

- **WHEN** the tool is called for a project whose cut point candidates are fewer than the budget
- **THEN** the result holds every candidate and states that number as the total

#### Scenario: The budget comes from the workspace

- **WHEN** a workspace names a value for the budget property
- **THEN** the tool bounds its lists by that value, and a workspace that names none is bounded by the seeded value

### Requirement: The anchor analysis reads the bounded candidates

The anchor analysis SHALL read the bounded candidate payload of the tool, the context map result and the marker
facts, and SHALL NOT call the project wide evidence tools itself, so that the size of its input does not grow with
the number of modules of the project.

#### Scenario: The anchor input does not grow with the project

- **WHEN** the anchor analysis runs on a project whose module count doubles
- **THEN** its input holds the bounded candidate lists of each anchor type, the context map result and the marker
  facts, and it holds no per-module evidence of the whole project

#### Scenario: The evidence of a candidate is carried

- **WHEN** the tool returns a candidate
- **THEN** the candidate names the evidence it rests on and the analysis that counted it, so the anchor the model
  forms from it can name its source
