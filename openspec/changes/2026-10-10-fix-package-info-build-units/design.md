# Design

## Context

See `proposal.md` — Why. Measured on this checkout:

- `Apache_Maven.api` (module `Java_Apache_Maven.api.json`) carries 20 build units named
  `<package>.package-info`; the Maven workspace carries 68 of them across 17 module reports (15 of which reach
  the analysis state and stop there), the jabref reports carry `.module-info`.
- `javac` writes `package-info.class` only when the package declaration carries an annotation, so the source
  tree of a project without annotated packages never produces one. `demo/package-info.java` with a bare
  `package demo;` compiles to no class file at all; the same file with `@Deprecated` in front compiles to
  `demo/package-info.class` with the flags `ACC_INTERFACE, ACC_ABSTRACT, ACC_SYNTHETIC` and with no field and
  no method. `module-info.java` compiles to `module-info.class` with the flag `ACC_MODULE`, the internal name
  `module-info` and no package name.
- The source path already excludes both: `JavaFileParser` returns early when `cu.getPrimaryType()` is empty,
  which is the case for `package-info.java` and `module-info.java`. Their package is still registered, so a
  package that holds nothing but a `package-info.java` appears as a `Package` without a build unit, which no
  graph node, no class count and no metric distribution looks at.
- `ClassFileParser.parseClassFile` computes the qualified name as
  `<packageName> + "." + <displayName>`, which yields `org.apache.maven.api.cache.package-info` for the Maven
  descriptors and `.module-info` for a module descriptor in the unnamed package.
- `IntraModuleGraph.projectNamespace` keeps a build unit whose name starts with the module namespace; the
  Maven `package-info` names are inside `org.apache.maven`, so they become nodes. `.module-info` is outside
  the jabref namespace and is dropped there by accident, not by rule.
- `DependencyDiagrams.SAFE_LABEL` is `[A-Za-z0-9_.$]+`; a hyphen fails it, and a single failing name returns a
  `DiagramSet` with no overview, no groups and one `notDrawn` reason for the whole module.

## Goals / Non-Goals

**Goals:**

- A class file that declares no class of the module never becomes a build unit, a graph node, a class count or
  a metric input.
- A report that a run reuses cannot carry the phantom classes into any analysis under the current engine.
- The rule is stated once and is used by the place that admits a class file.

**Non-Goals:**

- Relaxing the diagram emitter's name whitelist or its whole-module abort (see `proposal.md`, out of scope).
- Dropping the `Package` entry of a package whose only file was a `package-info.java`.
- Any change to the source path of the parser.

## Decisions

### D1: A shared predicate in `ParserHelper`, applied before the model is built

`ParserHelper` already holds the classifiers of what a parsed file is (`getCategoryOfFile`,
`modifyClassAttributesByCategory`), so the predicate belongs there:

```java
public static boolean isPackageOrModuleInfo(String qualifiedName)
```

It compares the last dot-separated segment of the qualified name with `package-info` and `module-info`.

`ClassFileParser.parseClassFile` calls it directly after computing the qualified name and before
`getOrAddPackageByName` / `getOrAddBuildUnitByName`, so neither the package nor the build unit of such a file is
created. The name computation moves into a small private helper, because the skip and the build unit name
must be derived from the same string.

*Alternatives considered*

- *Detect the class file flags* (`ACC_SYNTHETIC` plus empty field and method list, or `ACC_MODULE`): more
  faithful to what the compiler thinks the file is, but it needs two different flag rules, it depends on a
  compiler setting the flags the way the JDK's `javac` does today, and it says less about the intent. The name
  is what identifies the compilation unit, and it is what the failure message showed.
- *Filter in `IntraModuleGraph`*: the graph already excludes classes outside the project namespace, so one
  more filter there would repair the diagram and the graph-based analyses. Rejected as the only measure: the
  metric tools that read the module directly (`getVisibilityDistributionReport`, `getInheritanceReport`,
  `computeInheritanceDepth`, `getMethodComplexityReport`, `getMethodNestingDepthReport`,
  `getCyclomaticComplexityModuleReport`) and the inter-module namespace index would keep counting a phantom
  class, and with the report format version raised below no stale report reaches the graph in the first
  place, so the filter would guard a path that cannot be taken. The model is the single place where a class of
  the module is admitted; the fix belongs there.

### D2: The skipped file counts as a file that contributed no type

`ClassFileParser.parseClassFile` returns `false` for such a class file, which is the same answer
`JavaFileParser.parseJavaFile` gives for a `package-info.java` today, and the same answer the existing test
`JavaToolTest.unparsableFilesAreReportedPerModule` pins for the source path ("2 of 3 source file(s) were not
analysed" for one broken file and one `package-info.java`). The line reads "N of M file(s) were not analysed
(the detail per file is at DEBUG)", which stays true, and the reason per file is logged at DEBUG naming the
file and the type. No caller changes.

*Alternative considered*

- *Return `true` ("the file was read, it simply declares nothing we model")*: keeps the count of "not
  analysed" files lower, which reads better on a Maven module with 30 such descriptors. Rejected because the
  project already defines that count as "files that contributed no type" and counts a `package-info.java` in
  it; one contract for both parse paths is worth more than a smaller number in one log line.

### D3: Raise the raw module report format version to 3

`Module.CURRENT_REPORT_FORMAT_VERSION` becomes 3 with a javadoc line naming the exclusion. A report on disk was
written by a parser that admitted the compilation unit level types, so reusing it hands every metric tool a
class that the project does not have. The guard that reads the version lives in
`JavaTool.generateAllModuleAnalysisReports`, which then regenerates the module and states the reason, and in
the consumers (`generateDependencyDiagrams`, `getSplitCandidates`, `getGodClassRanking`,
`getClassDependencyGraph`), which refuse an outdated report instead of computing on it.

The cost is one re-parse per Java module per workspace, without any model call. The alternative — keep the
version at 2 and let stale reports keep the phantom class in their metric distributions — was rejected: a
number that counts a class the module does not have is exactly the kind of quiet wrongness the report version
mechanism exists to catch.

### D4: Test with the existing `javac` fixture, not with a copied class file

`ClassFileParserReferenceTest` already compiles Java sources with `ToolProvider.getSystemJavaCompiler()` into
a temporary directory and parses the resulting class files. An annotated `package-info.java` next to a
regular class, and a `module-info.java`, are compiled by the same helper, so the tests need no new
infrastructure and no checked-in binary fixture. The `parse` helper only writes files for types it can name,
so it gains a small variant that takes the file name of the source, which keeps the existing fixtures
unchanged.

## Risks / Trade-offs

- [A project may legitimately have a class in the default package whose name contains a hyphen]
  → impossible: a hyphen is not a Java identifier character, so `module-info` and `package-info` are the only
  names that can carry one.
- [A class named `package-info` in a package of its own, without being a package descriptor]
  → not expressible: a type name may not contain a hyphen, so a Java type named `package-info` cannot be
  declared. Only the compiler writes these files.
- [Report regeneration costs time on a large workspace] → offline, no model, and idempotent afterwards; the
  opt-in `RegenerateWorkspaceReportsTest` does it for all local workspaces in one run.
- [A package that holds nothing but a `package-info.java` stays as an empty `Package` in the report]
  → harmless by construction: no build unit, so no node, no class count and no metric entry; the parser has
  registered that package since before this change.
- [The engine could see a class file produced by a compiler other than `javac` that names a package descriptor
  differently] → the predicate matches the two names the JLS defines; another compiler that invents a third
  name would need its own rule, and the diagram would report it as it reports any unrepresentable name today.

## Migration Plan

1. Add the predicate and the skip; run the unit tests.
2. Raise the report format version; confirm `JavaToolTest` still describes the outcome (it uses version 1 as
   the outdated version and the version constant everywhere else).
3. Run `mvn test -Dllmaj.regenerateWorkspaces=true` to rewrite the local workspaces, then
   `mvn test -Dtest=RealModuleDiagramTest` and check that `target/dependency-diagrams` holds diagrams for the
   Maven modules that had none.
4. Rollback: revert the four Java files. Reports written as version 3 read as outdated for an older engine and
   are regenerated by it, so no report has to be repaired by hand.

## Open Questions

- Whether a later change should let the emitter keep drawing the rest of a module when one class name cannot
  be represented, and how the caption would state the skipped class.
- Whether the empty `Package` of a package that holds only a `package-info.java` should be dropped from the
  report at all.
