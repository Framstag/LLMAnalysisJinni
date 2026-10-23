# Spec Delta

## Purpose

Joins the counted evidence of a project into one ranked list of the places a modernisation can attach, and names the
modernisation patterns that fit each of them, so that a reader of the report can argue about a concrete place and a
concrete pattern instead of about the project in general.

## ADDED Requirements

### Requirement: Anchors are detected per type

The system SHALL provide an analysis that produces one list of anchors per anchor type, and the anchor types SHALL be
the entry point, the seam, the domain, the build unit, the cut point, the test coverage and the technology marker.
Each anchor SHALL name its type, the module it belongs to, the element it points at where the type has one, and the
evidence it rests on.

#### Scenario: Anchor names its element and its evidence

- **WHEN** the analysis finds a seam whose interface is implemented by three classes and imported by two modules
- **THEN** the anchor names the interface, the module that declares it, the implementor count and the importing
  modules as its evidence

#### Scenario: Domain anchor names the contexts it rests on

- **WHEN** the analysis finds a relation of the context map between two contexts
- **THEN** the anchor names both contexts, the relation and the shared language the relation rests on as its
  evidence

### Requirement: An anchor names the candidate patterns that fit it

The system SHALL name for every anchor the modernisation patterns that fit it, and SHALL take those patterns from the
catalogue the marker facts define, so that no pattern is invented outside the catalogue.

#### Scenario: Pattern outside the catalogue is not used

- **WHEN** the analysis judges an anchor and no pattern of the catalogue fits it
- **THEN** the anchor carries an empty candidate list and the reason, instead of a pattern name of its own making

#### Scenario: A candidate pattern is a candidate

- **WHEN** an anchor carries a candidate pattern
- **THEN** the anchor states what would make that pattern the wrong choice, so that the reader can disagree

### Requirement: The anchor strength is a rank, not a verdict

The system SHALL rank the anchors of a type by the evidence they rest on, and SHALL NOT classify an anchor as good or
bad, and SHALL NOT apply an absolute threshold that decides the outcome.

#### Scenario: Rank is relative to the project

- **WHEN** the analysis reports a seam that is the most implemented interface of the project
- **THEN** it states that it ranks first among the seams of this project, and it states no threshold that would
  decide the same outcome in another project

#### Scenario: No anchor is rejected for standing out

- **WHEN** an anchor stands out from its peers in the evidence it rests on
- **THEN** the anchor is reported, and it is not marked as a defect, a risk or a failure

### Requirement: Every number an anchor states can be named

The system SHALL report every count an anchor rests on with the analysis it was counted by, so that a reader can
find the counted evidence behind the anchor.

#### Scenario: Count names its source

- **WHEN** an anchor states an implementor count of three
- **THEN** the anchor names the seam analysis as the source of that count

### Requirement: An anchor type without its input is reported as absent

The system SHALL report an anchor type whose input analysis did not run as absent, with the reason, and SHALL NOT
silently leave the type out of the report.

#### Scenario: Context map did not run

- **WHEN** the analysis runs in a project where the context map analysis produced no result
- **THEN** the domain anchors are reported as absent with the reason that the context map did not run, and the
  anchor types whose inputs are present are still reported

### Requirement: The anchor analysis reads bounded evidence

The system SHALL read the counted evidence of the project and the result of the context map analysis, and SHALL NOT
read the prose findings of the other evaluation analyses, so that the size of the anchor analysis does not grow with
the number of findings of the project.

#### Scenario: Findings of other analyses do not reach the anchor analysis

- **WHEN** the anchor analysis runs in a project whose evaluation analyses produced many findings
- **THEN** the anchor analysis reads the counted evidence and the context map result, and not the findings of those
  analyses

### Requirement: The marker facts hold the marker knowledge

The system SHALL hold the namespaces that mark a legacy dependency, the version ranges that mark a dependency at the
end of its life, the annotations that mark an entry point, the interfaces that mark a service provider interface and
the catalogue of modernisation patterns in the marker facts of the analysis, and SHALL NOT hold them in the analysis
tools.

#### Scenario: Tools know no framework

- **WHEN** the seam tool and the entry point tool count the project
- **THEN** neither tool names a framework, an annotation that marks an entry point or a modernisation pattern

### Requirement: The anchor analysis states what it cannot see

The system SHALL state the limits of the anchor analysis in its result, including that the anchor types rest on
counted evidence of the Java modules alone and that no evidence of the history of the project is part of the
analysis.

#### Scenario: Limits are stated

- **WHEN** the anchor analysis reports its anchors
- **THEN** it states that no change frequency, no age of the code and no ownership is part of the analysis
