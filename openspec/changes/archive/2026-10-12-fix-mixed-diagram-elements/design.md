# Design

## The defect

`DependencyDiagrams.groupDiagram` writes one `class "<name>" as C<n>` line per member of the group and one
`rectangle "group <n> of <total> ..." as G<n>` line per other group the members couple to. PlantUML infers the
diagram type from the elements it reads, and a class diagram refuses an element of another family unless the
diagram declares `allowmixing`.

Collapsed groups are not an edge case of the emitter; they are its point. The class detail exists to make a
separation visible, and a separation is only visible if the other side of it appears in the picture:

```java
source.append("rectangle \"group ").append(otherIndex + 1).append(" of ").append(allGroups.size())
```

Every diagram whose group couples outward carries that line, so every such diagram was rejected. Measured on
the Maven workspace: 100 of 273 stored diagrams, 0 of them carrying the directive.

## Decision: declare the mix instead of avoiding it

Three ways out:

1. Write `allowmixing` into a diagram that collapses a group. The mix stays what it is, a picture of classes
   plus the boxes standing for the groups they were separated from.
2. Draw a collapsed group as a `class` with a stereotype. No directive needed, and no mixing. But a collapsed
   group is not a class of the module, and drawing it as one puts a claim into the picture that the caption
   then has to take back.
3. Draw a separate diagram per group without collapsed nodes. Loses exactly the information the picture is
   for.

Decision: 1. The directive states what the diagram does, which is the same principle the caption follows when
it says how many classes it shows and how many edges it omitted. A rectangle also reads as "not a class here",
which is the truth about a collapsed group.

The directive is written only when a collapsed group is drawn, so a diagram that collapses nothing keeps its
exact previous source. On the Maven data that is 173 of 273 diagrams unchanged.

## Decision: teach the guard the rule

The emitter checks its own source before storing it:

```java
Diagram diagram = diagram("group-" + (ownIndex + 1), ..., source.toString(),
        "the class detail could not be written as valid PlantUML source", notDrawn);
```

That check exists so a broken diagram is reported instead of stored, and it accepted this defect for two
releases. It cannot become a full parser without a PlantUML dependency, which the project does not take for
analysis logic, but this rule is cheap and exact: collect the leading word of every element declaration, and
require `allowmixing` when both families appear.

Verified against the renderer rather than assumed. A minimal source with `class` plus `rectangle` and no
directive returns HTTP 400 from `www.plantuml.com/plantuml/png`; the same source with `allowmixing` returns
HTTP 200 and a 11,795 byte PNG. The directive is accepted both before and after the `title` line, so its
position after the title is safe.

## Out of scope

The renderer's answer is also a check we could run in the build, but that needs network access in a unit test
and the repository's gate is offline. The check stays structural, and the real-data test keeps a sample of
emitted diagrams under it.
