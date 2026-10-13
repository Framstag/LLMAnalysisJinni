# Fix the diagrams that mix classes and collapsed groups

## Why

A class detail diagram draws its members as `class` elements and every other group of the separation as a
`rectangle`. PlantUML fixes the diagram type from the first element it reads, so such a source is read as a
class diagram, and a class diagram rejects the other element family:

```
Failed to generate image: PlantUML preprocessing failed: [From <input> (line 13) ]
rectangle "group 1 of 337 (12 class(es), mostly org.apache.maven.api, 4 outside it)" as G0
^^^^^
 Use 'allowmixing' if you want to mix classes and other UML elements. (Assumed diagram type: class)
```

The emitter wrote the directive nowhere, so every diagram that collapses at least one group was stored as
source no renderer accepts. In the Maven workspace that is 100 of its 273 diagrams, among them the group
diagrams of `Apache_Maven.api`, `Apache_Maven.impl` and `Apache_Maven.compat`. The document is correct, the
source in it is not, and the reader only finds out when their toolchain tries to draw the picture.

The structural check that guards the emission did not catch it, because it only asks for the `@startuml`
wrapper, a closed label on every line and at least one element. A source that mixes two element families
without saying so satisfies all three.

## What changes

- The class detail emitter writes `allowmixing` into a diagram as soon as it draws a collapsed group, so the
  mix the picture needs is declared.
- The structural check gains the rule that a source carrying a class element next to an element of another
  family has to carry `allowmixing`, so the guard refuses such source instead of storing it.
- The Maven workspace is regenerated so its stored diagrams are renderable.

## Impact

- `DependencyDiagrams`: the emitted group diagram source gains one line when it collapses a group, and
  `isValid` gains one rule. The overview is unaffected, because it draws packages only.
- Every diagram of every module that collapses a group becomes renderable, which is a larger effect than the
  change is small: the pictures were in the document all along, they simply could not be drawn.
- Purely derived data, no model involvement, so the fix is deterministic and the same report still yields the
  same source. Diagrams without a collapsed group keep their exact previous source.
