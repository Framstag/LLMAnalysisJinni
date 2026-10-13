# Tasks

## 1. Emitter

- [x] 1.1 Write `allowmixing` into the class detail source when at least one collapsed group is drawn, after
      the title, with a comment naming the renderer message it answers (`DependencyDiagrams.groupDiagram`)
- [x] 1.2 Leave a diagram without a collapsed group byte for byte as it was, so the fix changes only the
      diagrams that were unrenderable

## 2. Guard

- [x] 2.1 Teach `DependencyDiagrams.isValid` the element families: a source that declares a class element
      (`class`, `abstract`, `interface`, `annotation`, `enum`, `entity`, `struct`, `protocol`, `exception`,
      `metaclass`, `stereotype`) next to an element of another family (`rectangle`, `package`, `component`,
      `usecase`, `actor`, `node`, `cloud`, `database`, `folder`, `artifact`, `boundary`, `control`,
      `collections`, `queue`, `stack`, `storage`, `card`, `file`, `hexagon`, `label`) has to carry
      `allowmixing`
- [x] 2.2 Keep the existing checks (wrapper, closed label per line, at least one element) and extend the
      javadoc with the rule

## 3. Tests

- [x] 3.1 `theClassDetailAllowsMixingWhenItCollapsesGroups`: the group diagram of the fixture carries
      `allowmixing`, passes `isValid`, and the package overview of the same module does not carry it
- [x] 3.2 Extend `theGeneratedSourceIsValidated` with the mixing rule: class plus rectangle without the
      directive is rejected, the same source with the directive is accepted, and a package-only source stays
      valid without the directive
- [x] 3.3 `mvn verify`: 391 unit tests and 6 smoke tests, BUILD SUCCESS

## 4. Verification against the renderer

- [x] 4.1 Reproduce the rejection with the official renderer: the source of the diagram from the report
      returns HTTP 400 from `www.plantuml.com/plantuml/png`, and HTTP 200 with 11,795 bytes once `allowmixing`
      is added
- [x] 4.2 Regenerate the Maven diagrams (drop `ClassDependencyGraphEvaluationAll`, rerun `analyse`, then
      `document`) and confirm that no stored diagram mixes families without the directive and that the 100
      diagrams which needed it carry it
- [x] 4.3 Render a spread sample of those diagrams through the renderer and confirm HTTP 200 for each,
      including `Apache_Maven.api - group 2 of 337`, the diagram of the report
