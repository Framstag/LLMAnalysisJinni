package com.framstag.llmaj.tools.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        Path sourceDir = tempDir.resolve("src/demo");
        Files.createDirectories(sourceDir);

        List<String> sourceFiles = new java.util.ArrayList<>();
        for (String source : sources) {
            // A public type has to live in a file of its own name, otherwise javac refuses the fixture.
            Path sourceFile = sourceDir.resolve(publicTypeName(source) + ".java");
            Files.writeString(sourceFile, source);
            sourceFiles.add(sourceFile.toString());
        }

        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> arguments = new java.util.ArrayList<>(List.of("-d", classesDir.toString()));
        arguments.addAll(sourceFiles);

        int compilerResult = compiler.run(null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, compilerResult, "the fixture must compile");

        ModuleManager moduleManager = new ModuleManager("core");

        try (var classFiles = Files.walk(classesDir)) {
            for (Path classFile : classFiles.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassFileParser.parseClassFile(classFile, List.of(), moduleManager);
            }
        }

        return moduleManager;
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
}
