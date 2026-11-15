# modernisation-anchor-detection Specification

## Purpose
Joins the counted evidence of a project into one ranked list of the places a modernisation can attach, and names the
modernisation patterns that fit each of them, so that a reader of the report can argue about a concrete place and a
concrete pattern instead of about the project in general.

## Requirements

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

### Requirement: A candidate is reported once per element

The system SHALL report an anchor candidate once per element, naming the module the element is read in and the other
modules whose reports hold it, instead of reporting the element once per module report, so that the budget of a
candidate list is not spent on the same element several times.

#### Scenario: An element that several module reports hold

- **WHEN** an interface of the project appears in the reports of three modules, because the aggregator module holds the
  same classes as its inner modules
- **THEN** the candidate list holds one candidate for that interface, and the candidate names the module it is read in
  and the other two modules

#### Scenario: The ranks are not pushed down by other appearances

- **WHEN** the strongest seam of the project appears in three module reports
- **THEN** it holds one rank, and the seam after it holds the next rank rather than the fourth

#### Scenario: An element of a single module

- **WHEN** an element appears in the report of one module only
- **THEN** its candidate names that module and holds no further module

### Requirement: A production entry point ranks above test code

The system SHALL rank an entry point candidate of production code above an entry point candidate of test or generated
code, so that the budget of the entry point list is spent on the places a caller reaches the product through.

#### Scenario: A benchmark main is not the strongest entry point

- **WHEN** the project holds a production class with a main method and a benchmark harness with a main method and more
  annotations
- **THEN** the production class ranks before the benchmark harness

#### Scenario: A test entry point is still reported

- **WHEN** an entry point candidate is test or generated code
- **THEN** it stays in the list, below the production candidates, and its evidence states that it is test or generated
  code, so that a reader sees what the list holds and does not read a test harness as a product entry

### Requirement: The technology marker rests on the namespace evidence

The system SHALL report a technology marker from the consolidated external namespace evidence when the marker facts
key that marker by a namespace, and the evidence of the anchor SHALL name the consolidated namespace evidence as the
source it was counted by.

#### Scenario: A legacy namespace without dependency data

- **WHEN** a project holds no dependency data and its external namespaces include one of the legacy namespaces of the
  marker facts
- **THEN** the anchor analysis reports a technology marker that names that namespace
- **AND** the evidence of the anchor names the consolidated namespace evidence as its source

#### Scenario: The namespace is read from the imports

- **WHEN** a technology marker rests on the namespace evidence
- **THEN** it is derived from the namespaces the project's own code imports, and not from a dependency that the
  project declares

### Requirement: The version range marker is absent without dependency data

The system SHALL report the version range half of the technology marker as unavailable when the project holds no
dependency data, with a reason that names the missing data, and SHALL NOT report it as absent without a reason and
SHALL NOT form it from the namespace evidence.

#### Scenario: No dependency data

- **WHEN** the project holds no dependency data and the anchor analysis reports its anchor types
- **THEN** the version range markers are reported as unavailable, and the reason names the missing dependency data
- **AND** no anchor reports a version range of a dependency

#### Scenario: The namespace half is unaffected

- **WHEN** the version range markers are reported as unavailable
- **THEN** the technology markers that the marker facts key by a namespace are still reported, when their namespace is
  part of the consolidated namespace evidence

### Requirement: The two halves of the technology marker are named apart

The system SHALL report the namespace half and the version range half of the technology marker as two parts of one
anchor type, so that a reader sees which part is present and which part is missing, and SHALL NOT report the whole
type as absent when only one of its halves is missing.

#### Scenario: One half is present and one half is missing

- **WHEN** the project holds the namespace evidence but no dependency data
- **THEN** the report holds the namespace markers
- **AND** it states that the version range markers are unavailable, and it does not report the type as absent

#### Scenario: Both halves are missing

- **WHEN** the project holds neither the namespace evidence nor dependency data
- **THEN** the type is reported as absent, and the reason names both missing inputs

### Requirement: The marker knowledge stays in the marker facts

The system SHALL take the namespaces that mark a legacy dependency and the version ranges that mark a dependency at
the end of its life from the marker facts, and SHALL NOT hold them in the tool that consolidates the namespace
evidence.

#### Scenario: The consolidation tool knows no framework

- **WHEN** the namespace evidence is consolidated
- **THEN** no tool names a legacy namespace, a counterpart namespace or a version range

#### Scenario: A namespace the marker facts do not name

- **WHEN** the consolidated evidence holds a namespace that the marker facts do not name as a legacy namespace
- **THEN** no technology marker is reported for it
