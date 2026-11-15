# Spec Delta

## Purpose

Defines what the analysis may claim about the technology of a project when no dependency data exists: a consolidated,
bounded list of the external namespaces the project's own imports name, and the classification of those namespaces
into technologies, without any claim about an artifact, a version or a license.

## ADDED Requirements

### Requirement: The external namespaces of the project are consolidated

The system SHALL provide a tool that returns the external namespace prefixes of the whole project, derived from the
import distribution the analysis has already counted, grouped by the first segment of the prefix, each group carrying
the prefixes it holds and the import count of each.

#### Scenario: A project with several modules is consolidated into one list

- **WHEN** the tool is called for a project whose import distribution was counted for several modules
- **THEN** it returns one list for the whole project, not one list per module
- **AND** the import counts of a prefix that several modules import are summed into one entry

#### Scenario: A group carries its prefixes and their counts

- **WHEN** a group of the result holds more than one prefix
- **THEN** it names every prefix it holds and the import count of each of them

### Requirement: Only production code is evidence

The consolidated evidence SHALL be derived from the imports of production code and SHALL NOT count the imports of
test code or of generated code.

#### Scenario: A library only the tests import

- **WHEN** a namespace is imported by test code only
- **THEN** it is not part of the consolidated evidence

#### Scenario: A library that production and test code import

- **WHEN** a namespace is imported by production code and by test code
- **THEN** it is part of the consolidated evidence, and its count is the count of the production imports

### Requirement: The evidence names no artifact and no version

The system SHALL NOT name a dependency artifact, a dependency version, a license or a transitive dependency in the
consolidated evidence, and SHALL NOT derive any of them from a namespace.

#### Scenario: The evidence holds namespaces only

- **WHEN** the consolidated evidence is returned
- **THEN** every entry it holds is a namespace prefix and a count
- **AND** no entry holds a version, an artifact name or a license

### Requirement: Platform namespaces are not evidence

The consolidated evidence SHALL NOT hold the namespaces of the Java platform itself, that is the prefixes below
`java.`, `jdk.`, `sun.` and `com.sun.`.

#### Scenario: A project that imports java.util

- **WHEN** the project imports classes of `java.util`
- **THEN** no entry of the consolidated evidence names `java.util`

#### Scenario: A JDK internal namespace

- **WHEN** the project imports classes below `com.sun.`
- **THEN** no entry of the consolidated evidence names it

### Requirement: The Jakarta namespaces are evidence

The consolidated evidence SHALL keep the prefixes below `javax.` and `jakarta.`, because the marker facts of the
analysis key a legacy dependency by those namespaces.

#### Scenario: A project on the javax generation of Jakarta EE

- **WHEN** the project imports classes below `javax.persistence` or `javax.servlet`
- **THEN** the consolidated evidence holds those prefixes
- **AND** the classification can report them

#### Scenario: A namespace that is not filtered as a platform namespace

- **WHEN** the consolidated evidence is built
- **THEN** no prefix below `javax.` or `jakarta.` is removed by the rule that removes the platform namespaces

### Requirement: Type-shaped and array entries are not namespaces

The consolidated evidence SHALL NOT hold an entry that names a type rather than a package, which is an array
descriptor or an entry whose second segment names a type.

#### Scenario: An array descriptor reaches the prefix report

- **WHEN** the import distribution holds an entry like `[Lorg.apache`
- **THEN** the consolidated evidence does not hold it

#### Scenario: A package with a single sub package

- **WHEN** the import distribution holds an entry whose second segment names a type, as in `picocli.CommandLine`
- **THEN** the consolidated evidence does not report that segment as a namespace

### Requirement: The project's own namespaces are not evidence

The consolidated evidence SHALL NOT hold a namespace of the project that is analysed, and SHALL consider every
namespace of the project for that rule, not one of them.

#### Scenario: A project with more than one root package

- **WHEN** the project declares classes in more than one root package
- **THEN** no namespace of any of them is part of the consolidated evidence

#### Scenario: An inner namespace of the project

- **WHEN** a prefix of the project's own namespace appears as an import source
- **THEN** it is not part of the consolidated evidence

### Requirement: The consolidated list is bounded and states its total

The system SHALL bound the number of groups of the consolidated evidence by a property of the workspace, SHALL state
the total number of groups the project holds beside the bounded list, and SHALL state that the list is a part of the
groups and not the whole of them when the two numbers differ.

#### Scenario: A project with more groups than the bound

- **WHEN** the project holds more groups than the bound of the workspace
- **THEN** the result holds the groups with the highest import count up to the bound
- **AND** it states the total number of groups of the project

#### Scenario: The bound comes from the workspace

- **WHEN** a workspace names a value for the bound property
- **THEN** the tool bounds its list by that value, and a workspace that names none is bounded by the seeded value

#### Scenario: A project with fewer groups than the bound

- **WHEN** the project holds fewer groups than the bound
- **THEN** the result holds every group and states that number as the total

### Requirement: The namespaces are classified into technologies

The system SHALL provide an analysis that names the technologies the project uses, each resting on the groups of the
consolidated evidence that name it, and SHALL name the groups it could not place instead of placing them.

#### Scenario: A group that a known technology explains

- **WHEN** the consolidated evidence holds a group that names a known technology
- **THEN** the result names that technology and the group it rests on

#### Scenario: A group that the analysis cannot place

- **WHEN** a group of the consolidated evidence names no technology the analysis recognises
- **THEN** the group is reported as unplaced, and no technology is named for it

#### Scenario: The classification states a limit

- **WHEN** the classification reports its technologies
- **THEN** it states that a group is coarser than an artifact, so that a technology is named and an artifact is not

### Requirement: The classification claims no version

The classification SHALL NOT state a version of a technology, and SHALL state that no version is part of its result
because the evidence it rests on carries none.

#### Scenario: A technology without a version

- **WHEN** the classification names a technology
- **THEN** it names no version of it
- **AND** it states that the evidence carries no version

#### Scenario: A project without dependency data

- **WHEN** the project holds no dependency data and the classification reports its technologies
- **THEN** the response validates against its schema and the task is successful
- **AND** the response does not carry a version and names the missing data as the reason it carries none

### Requirement: A project without parsed imports yields an empty evidence

The system SHALL return an empty consolidated evidence with a reason that names the missing input when the import
distribution of the project holds no external namespace, and SHALL name the absence instead of returning an empty list
without a reason.

#### Scenario: A project with no parsed Java module

- **WHEN** the tool is called for a project whose modules hold no parsed import
- **THEN** the result is empty
- **AND** it names the missing input as the reason

#### Scenario: A project whose imports are all internal

- **WHEN** every import of the project resolves to a namespace of the project itself
- **THEN** the result is empty and the reason states that the project imports nothing external
