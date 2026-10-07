# Spec Delta

## Purpose

Defines what the model is told about the shape of the answer it has to produce, and the rule that a
declared response schema of an analysis domain is satisfiable: the format description rendered into the
prompt covers the whole schema, and a schema declares every property it requires.

## ADDED Requirements

### Requirement: The response format description covers the whole schema

The description of a response schema rendered into the prompt SHALL describe every property of the
schema, including the properties of nested objects and of array items, at every nesting level, whether
or not an array's item schema carries a `title`. For every object level the description SHALL name the
properties that level requires, and for every enumerated property it SHALL name the values the schema
permits, spelled exactly as the schema spells them.

#### Scenario: Untitled array items are described
- **WHEN** a schema declares an array whose `items` describe an object without a `title`
- **THEN** the description SHALL name the properties of that item object
- **AND** the description SHALL NOT render the property as a bare `type: array`

#### Scenario: Nested levels are described
- **WHEN** a schema declares an object whose property is an array of objects that themselves contain an array of objects
- **THEN** both levels of properties SHALL appear in the description

#### Scenario: Enumerated values are named
- **WHEN** a schema declares `enum` for a property, at any nesting level
- **THEN** the description SHALL list the permitted values for that property

#### Scenario: Required properties are named
- **WHEN** a schema declares a property as required
- **THEN** the description SHALL make the required status of that property visible at its object level

#### Scenario: The nesting of the description is bounded
- **WHEN** a schema nests objects beyond the depth the description renders
- **THEN** the description SHALL stop descending at that bound
- **AND** it SHALL NOT render a schema without bound or fail

#### Scenario: The batch metric schema is described completely
- **WHEN** the description of `results/ModuleBatchEvaluation.json` is rendered
- **THEN** it SHALL contain the properties of a `moduleEvaluations[]` item and of an `evaluations[]`
  entry, including `urgency` and `criticality`
- **AND** it SHALL name `NONE`, `LOW`, `MEDIUM` and `HIGH` as the permitted values of those properties

### Requirement: A declared response schema declares every property it requires

Every response schema shipped by an analysis domain SHALL declare, in its `properties`, each name it
lists in `required`. A schema that requires a property it does not declare SHALL be treated as a defect
of the domain, not as a model failure, because no answer can satisfy it.

#### Scenario: Required names have declarations
- **WHEN** every `analysis/*/results/*.json` schema is inspected
- **THEN** each name in a `required` list SHALL have an entry in the `properties` of the same schema
- **AND** the same SHALL hold for nested object schemas

#### Scenario: The defect is reported where it is cheap to fix
- **WHEN** a schema requires a property it does not declare
- **THEN** the check over the domain's schemas SHALL fail and name the schema and the property
- **AND** the engine SHALL NOT refuse to run the analysis because of it

#### Scenario: The technology stack schema is satisfiable
- **WHEN** the response of the technology stack task contains a reasoning, a summary, a technology list
  and a derivation list, and the project has no dependency data
- **THEN** that response SHALL validate against the schema of the task
- **AND** the task SHALL be marked successful
