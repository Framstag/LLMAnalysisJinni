# Proposal

## Why

15 of the 87 Java module reports of the local Maven workspace produce no dependency diagram at all. Every one
of them reports the same reason:

```text
No diagram was generated: the class name(s) [org.apache.maven.api.annotations.package-info,
org.apache.maven.api.cache.package-info, ...] cannot be written into PlantUML source without breaking it.
```

The listed names are not classes of the module. `javac` writes a `package-info.class` beside every
`package-info.java` whose package declaration carries an annotation with class or runtime retention, and the
`org.apache.maven.api.*` packages are annotated: `Apache_Maven.api` alone carries 20 such descriptors, the
Maven workspace carries 68 of them across 17 module reports. `ClassFileParser.parseClassFile` turns every class
file into a build unit without asking what the file declares, so a package annotation descriptor becomes a
class of the module with the qualified name `<package>.package-info`. The same defect hits `module-info.class`,
which the jabref reports carry as the class `.module-info` and which only escapes the diagrams because it falls
outside those modules' project namespace.

The damage is not limited to the diagram. `DependencyDiagrams` rejects a name outside its whitelist and stops
the emission for the whole module, so one package annotation descriptor costs the module both the package
overview and every class detail diagram. And the phantom class is a node of the class reference graph, it is
counted in the class count, and it enters the inheritance, visibility, cyclomatic complexity, nesting depth
and method complexity distributions as a class without a method whose superclass is `java.lang.Object` — in
the inheritance report of the Maven modules it even counts as a class that implements
`java.lang.annotation.Annotation`. The analysis reports a class that the project does not have.

## What Changes

- Add one shared predicate for the two compilation unit level types, `package-info` and `module-info`, to
  `ParserHelper`, beside the other classifiers of what a parsed file is.
- Let `ClassFileParser.parseClassFile` drop such a class file before any package or build unit is created, so
  the type never enters the model. The file is reported like the source path already reports a
  `package-info.java`: as a file that contributed no type of the module, with the reason at DEBUG.
- Bump `Module.CURRENT_REPORT_FORMAT_VERSION` from 2 to 3. A report that a run reuses was written by a parser
  that admitted those types, so it carries the phantom classes in its metric inputs; the version makes the
  report task regenerate it and state the reason instead of reusing it quietly.
- Cover the change with unit tests: a compiled `package-info.class` and `module-info.class` produce no build
  unit, and the classes beside them are unaffected.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `class-dependency-graph`: the model admits only types that declare a class of the module, so a compilation
  unit level type is neither a node of the reference graph nor a class of the raw module report, and a reused
  report written before that rule is regenerated rather than reused.

## Impact

- `src/main/java/com/framstag/llmaj/tools/java/ParserHelper.java` — the new predicate.
- `src/main/java/com/framstag/llmaj/tools/java/ClassFileParser.java` — the skip before the model is touched,
  including the shared name computation, which both the skip and the build unit name use.
- `src/main/java/com/framstag/llmaj/tools/java/Module.java` — report format version 3 and its javadoc.
- `src/test/java/com/framstag/llmaj/tools/java/ClassFileParserReferenceTest.java` — the fixtures compile with
  `javac` already, so the two new cases need no new test infrastructure.
- No analysis domain, task definition, prompt or template changes. No new dependency. No change to the
  diagram emitter: it keeps refusing a name it cannot represent, it just no longer sees one.
- Local effect: every Java module report is regenerated once. `RegenerateWorkspaceReportsTest` (opt in with
  `-Dllmaj.regenerateWorkspaces=true`) does that for the local workspaces, and `RealModuleDiagramTest` then
  draws a diagram for the Maven modules that had none.
- Out of scope: the emitter's whole-module abort for a name it cannot represent stays as it is — a Java
  identifier is Unicode, so a class named `demo.Café` would still cost the module its diagrams. Skipping that
  class instead would put a picture beside a caption that does not count it, which the diagram spec forbids.
  That is a separate decision about the caption, not about `package-info`.
