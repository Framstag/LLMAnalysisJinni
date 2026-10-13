package com.framstag.llmaj.tools.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The class file visit is where the strength of a reference becomes observable. Calling one member many times
 * must widen the traffic but not the interface, calling several members must widen both, and an inheritance
 * that is never used must stay a structural relation without a measured reference site.
 */
class ClassFileParserReferenceTest {
    @TempDir
    Path tempDir;

    private ModuleManager parse(String... sources) throws Exception {
        List<Path> sourceFiles = new java.util.ArrayList<>();
        for (String source : sources) {
            // A public type has to live in a file of its own name, otherwise javac refuses the fixture.
            sourceFiles.add(writeSource(publicTypeName(source), source));
        }

        return parseFiles("classes", sourceFiles).moduleManager();
    }

    /** Writes a source file into the fixture package {@code demo}. */
    private Path writeSource(String fileName, String source) throws Exception {
        return writeSource(tempDir.resolve("src/demo"), fileName, source);
    }

    /** Writes a source file into a directory below the fixture tree, for a file outside a package. */
    private Path writeSource(Path directory, String fileName, String source) throws Exception {
        Files.createDirectories(directory);

        Path sourceFile = directory.resolve(fileName + ".java");
        Files.writeString(sourceFile, source);

        return sourceFile;
    }

    /**
     * The outcome of one class file visit: the module the files contributed to, and per class file whether it
     * contributed a type, which is what the caller counts as a file the module did not analyse.
     */
    private record ClassFileVisit(ModuleManager moduleManager, Map<String, Boolean> contributedByFileName) {
        private boolean contributed(String fileName) {
            Boolean contributed = contributedByFileName.get(fileName);

            if (contributed == null) {
                throw new AssertionError("no class file named " + fileName
                        + " in " + contributedByFileName.keySet());
            }

            return contributed;
        }
    }

    private ClassFileVisit parseFiles(String classesDirectoryName, List<Path> sourceFiles) throws Exception {
        Path classesDir = tempDir.resolve(classesDirectoryName);
        Files.createDirectories(classesDir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new java.util.ArrayList<>(List.of("-d", classesDir.toString()));
        sourceFiles.forEach(sourceFile -> arguments.add(sourceFile.toString()));

        int compilerResult = compiler.run(null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, compilerResult, "the fixture must compile");

        ModuleManager moduleManager = new ModuleManager("core");
        Map<String, Boolean> contributedByFileName = new java.util.TreeMap<>();

        try (var classFiles = Files.walk(classesDir)) {
            for (Path classFile : classFiles.filter(path -> path.toString().endsWith(".class")).toList()) {
                contributedByFileName.put(classFile.getFileName().toString(),
                        ClassFileParser.parseClassFile(classFile, List.of(), moduleManager));
            }
        }

        return new ClassFileVisit(moduleManager, contributedByFileName);
    }

    private static List<String> namesOf(ModuleManager moduleManager) {
        return moduleManager.getPackages().stream()
                .flatMap(pck -> pck.getBuildUnits().stream())
                .map(BuildUnitManager::getName)
                .toList();
    }

    private static String publicTypeName(String source) {
        var matcher = java.util.regex.Pattern
                .compile("public\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum)\\s+(\\w+)")
                .matcher(source);

        if (!matcher.find()) {
            throw new IllegalArgumentException("the fixture has no public type: " + source);
        }

        return matcher.group(1);
    }

    private static BuildUnitManager buildUnitNamed(ModuleManager moduleManager, String name) {
        return moduleManager.getPackages().stream()
                .flatMap(pck -> pck.getBuildUnits().stream())
                .filter(buildUnit -> buildUnit.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no build unit named " + name));
    }

    private static ClassReference referenceTo(BuildUnitManager buildUnit, String target) {
        return buildUnit.getReferences().stream()
                .filter(reference -> reference.getTarget().equals(target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no reference to " + target
                        + " in " + buildUnit.getReferences()));
    }

    private static Method methodNamed(BuildUnitManager buildUnit, String className, String methodName) {
        ClassManager clazz = buildUnit.getClasses().stream()
                .filter(candidate -> candidate.getQualifiedName().equals(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no class named " + className));

        return clazz.getMethods().stream()
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no method named " + methodName + " in " + className));
    }

    private static final String TARGET = """
            package demo;

            public class Target {
                public int value;

                public int getValue() {
                    return value;
                }

                public int other() {
                    return 1;
                }

                public int third() {
                    return 2;
                }
            }
            """;

    private static final String SOURCE = """
            package demo;

            public class Source {
                private int ownField;

                public int repeatedCall(Target target) {
                    return target.getValue() + target.getValue() + target.getValue();
                }

                public int distinctCalls(Target target) {
                    return target.getValue() + target.other() + target.third();
                }

                public int ownFieldRead() {
                    return ownField;
                }

                public int foreignFieldRead(Target target) {
                    return target.value;
                }
            }
            """;

    @Test
    void repetitionWidensTheTrafficButNotTheInterface() throws Exception {
        BuildUnitManager buildUnit = buildUnitNamed(parse(TARGET, SOURCE), "demo.Source");
        Method repeatedCall = methodNamed(buildUnit, "demo.Source", "repeatedCall");

        assertEquals(3, repeatedCall.getForeignCalls(),
                "three call sites to the same member must be counted three times");
        assertEquals(0, repeatedCall.getForeignFieldAccesses());
        assertEquals(0, repeatedCall.getInternalCalls());
        assertEquals(0, repeatedCall.getInternalFieldAccesses());
    }

    @Test
    void distinctMembersWidenTheInterface() throws Exception {
        BuildUnitManager buildUnit = buildUnitNamed(parse(TARGET, SOURCE), "demo.Source");
        Method distinctCalls = methodNamed(buildUnit, "demo.Source", "distinctCalls");

        assertEquals(3, distinctCalls.getForeignCalls(),
                "three call sites to three different members must be counted three times");

        ClassReference reference = referenceTo(buildUnit, "demo.Target");

        assertEquals(4, reference.getApiWidth(),
                "getValue, other, third and the field are four distinct members");
        assertEquals(7, reference.getTraffic(),
                "three repeated plus three distinct calls plus one field access are seven sites");
    }

    @Test
    void aMembersOwnFieldIsAnInternalAccess() throws Exception {
        BuildUnitManager buildUnit = buildUnitNamed(parse(TARGET, SOURCE), "demo.Source");
        Method ownFieldRead = methodNamed(buildUnit, "demo.Source", "ownFieldRead");

        assertEquals(1, ownFieldRead.getInternalFieldAccesses());
        assertEquals(0, ownFieldRead.getForeignFieldAccesses());
    }

    @Test
    void aForeignFieldIsAForeignAccessAndAReferenceSite() throws Exception {
        BuildUnitManager buildUnit = buildUnitNamed(parse(TARGET, SOURCE), "demo.Source");
        Method foreignFieldRead = methodNamed(buildUnit, "demo.Source", "foreignFieldRead");

        assertEquals(1, foreignFieldRead.getForeignFieldAccesses());
        assertEquals(0, foreignFieldRead.getInternalFieldAccesses());

        ClassReference reference = referenceTo(buildUnit, "demo.Target");
        assertTrue(reference.getTraffic() >= 1, "a foreign field access is a reference site");
    }

    @Test
    void aClassFileIsNotReferencedByItself() throws Exception {
        BuildUnitManager buildUnit = buildUnitNamed(parse(TARGET, SOURCE), "demo.Source");

        assertTrue(buildUnit.getReferences().stream()
                        .noneMatch(reference -> reference.getTarget().equals("demo.Source")),
                "a reference inside the same build unit is internal and must not become an edge");
    }

    private static final String MARKER = """
            package demo;

            public interface Marker {
            }
            """;

    private static final String BASE = """
            package demo;

            public class Base implements Marker {
            }
            """;

    private static final String DERIVED = """
            package demo;

            public class Derived extends Base {
                private Base delegate;
            }
            """;

    @Test
    void anInheritanceThatIsNeverUsedStaysStructuralWithoutAReferenceSite() throws Exception {
        ModuleManager moduleManager = parse(MARKER, BASE, DERIVED);
        BuildUnitManager derived = buildUnitNamed(moduleManager, "demo.Derived");

        ClassReference reference = referenceTo(derived, "demo.Base");

        assertTrue(reference.isStructural(), "extends is a structural relation");
        assertEquals(0, reference.getTraffic(),
                "the compiler emitted superclass constructor call must not count as a coupling");
        assertEquals(0, reference.getApiWidth());
        assertTrue(reference.isStructuralOnly());
    }

    @Test
    void anInheritanceOnlyRelationProducesNoReferenceEdge() throws Exception {
        BuildUnitManager derived = buildUnitNamed(parse(MARKER, BASE, DERIVED), "demo.Derived");

        assertTrue(derived.getReferences().stream().allMatch(reference -> reference.getTraffic() == 0),
                "a class that only extends and declares a field carries no reference site, got: "
                        + derived.getReferences());
    }

    @Test
    void anImplementedInterfaceIsStructural() throws Exception {
        BuildUnitManager base = buildUnitNamed(parse(MARKER, BASE, DERIVED), "demo.Base");

        ClassReference reference = referenceTo(base, "demo.Marker");

        assertTrue(reference.isStructural());
        assertEquals(0, reference.getTraffic());
        assertFalse(reference.isStructuralOnly() && reference.getApiWidth() > 0);
    }

    private static final String ANNOTATED_PACKAGE_INFO = """
            @Deprecated
            package demo;
            """;

    private static final String REGULAR_CLASS = """
            package demo;

            public class Regular {
            }
            """;

    /**
     * javac writes a package-info.class for an annotated package-info.java. It carries the annotations of the
     * package and declares no class of the module, so it must not become one: the name it would get carries a
     * hyphen, and one such name stopped the dependency diagram of a whole module before.
     */
    @Test
    void anAnnotatedPackageInfoDoesNotBecomeAClassOfTheModule() throws Exception {
        ClassFileVisit visit = parseFiles("classes-package-info", List.of(
                writeSource("package-info", ANNOTATED_PACKAGE_INFO),
                writeSource("Regular", REGULAR_CLASS)));

        assertTrue(visit.contributedByFileName().containsKey("package-info.class"),
                "the fixture has to produce a package-info.class, otherwise the test proves nothing");
        assertFalse(visit.contributed("package-info.class"),
                "the descriptor declares no class of the module, so the file counts as one that contributed"
                        + " no type");
        assertTrue(visit.contributed("Regular.class"),
                "a regular class beside the descriptor is a type of the module");

        assertTrue(namesOf(visit.moduleManager()).stream().noneMatch(name -> name.contains("package-info")),
                "no build unit may be a package annotation descriptor, got: "
                        + namesOf(visit.moduleManager()));
        assertTrue(namesOf(visit.moduleManager()).contains("demo.Regular"),
                "the class beside the descriptor is still parsed, got: " + namesOf(visit.moduleManager()));
    }

    @Test
    void aModuleDescriptorDoesNotBecomeAClassOfTheModule() throws Exception {
        ClassFileVisit visit = parseFiles("classes-module-info", List.of(
                writeSource(tempDir.resolve("src"), "module-info", "module demo.mod {\n}\n")));

        assertTrue(visit.contributedByFileName().containsKey("module-info.class"),
                "the fixture has to produce a module-info.class, otherwise the test proves nothing");
        assertFalse(visit.contributed("module-info.class"),
                "the descriptor declares no class of the module, so the file counts as one that contributed"
                        + " no type");

        assertTrue(visit.moduleManager().getPackages().isEmpty(),
                "a module descriptor carries no package of the module, got: "
                        + visit.moduleManager().getPackages().stream().map(PackageManager::getName).toList());
    }
}
