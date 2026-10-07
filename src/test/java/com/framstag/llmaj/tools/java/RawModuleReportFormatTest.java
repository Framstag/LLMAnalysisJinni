package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The raw module report is the contract between the class file parser and every Java metric tool, and an
 * existing report file is reused instead of re-parsing the module. A report written before the weighted
 * reference record existed must therefore be recognisable as outdated instead of reading as a module that
 * happens to have no references.
 */
class RawModuleReportFormatTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private Module moduleWithWeightedReferences() {
        Method method = new Method("use", "()V");
        method.setInternalFieldAccesses(3);
        method.setForeignFieldAccesses(5);
        method.setInternalCalls(7);
        method.setForeignCalls(11);

        Clazz clazz = new Clazz("demo.Source", null);
        clazz.addMethod(method);

        BuildUnit buildUnit = new BuildUnit("demo.Source", true, false,
                List.of("demo.Target"),
                List.of(new ClassReference("demo.Target", 4, 9, false),
                        new ClassReference("demo.Base", 0, 0, true)),
                List.of(clazz));

        Package pck = new Package("demo");
        pck.addBuildUnit(buildUnit);

        Module module = new Module("core");
        module.addPackage(pck);

        return module;
    }

    private Module roundTrip(Module module) throws Exception {
        Path reportFile = tempDir.resolve("Java/Java_core.json");
        Files.createDirectories(reportFile.getParent());
        objectMapper.writeValue(reportFile.toFile(), module);

        return objectMapper.readValue(reportFile.toFile(), Module.class);
    }

    private static BuildUnit firstBuildUnit(Module module) {
        return module.getPackages().getFirst().getBuildUnits().getFirst();
    }

    private static ClassReference referenceTo(BuildUnit buildUnit, String target) {
        return buildUnit.getReferences().stream()
                .filter(reference -> reference.getTarget().equals(target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no reference to " + target
                        + " in " + buildUnit.getReferences()));
    }

    @Test
    void weightedReferencesSurviveARoundTrip() throws Exception {
        BuildUnit buildUnit = firstBuildUnit(roundTrip(moduleWithWeightedReferences()));

        assertEquals(2, buildUnit.getReferences().size(),
                "both recorded references must survive the round trip");

        ClassReference measured = referenceTo(buildUnit, "demo.Target");
        assertEquals(4, measured.getApiWidth(), "the API width must survive the round trip");
        assertEquals(9, measured.getTraffic(), "the traffic must survive the round trip");
        assertFalse(measured.isStructural(), "a measured reference must not become structural");
        assertFalse(measured.isStructuralOnly(), "a measured reference carries a separation cost");

        ClassReference structural = referenceTo(buildUnit, "demo.Base");
        assertTrue(structural.isStructural(), "the structural flag must survive the round trip");
        assertEquals(0, structural.getTraffic(), "a structural relation has no reference site");
        assertTrue(structural.isStructuralOnly(), "a structural relation alone carries no separation cost");
    }

    @Test
    void perMethodAccessCountersSurviveARoundTrip() throws Exception {
        BuildUnit buildUnit = firstBuildUnit(roundTrip(moduleWithWeightedReferences()));
        Method method = buildUnit.getClazzes().getFirst().getMethods().getFirst();

        assertEquals(3, method.getInternalFieldAccesses());
        assertEquals(5, method.getForeignFieldAccesses());
        assertEquals(7, method.getInternalCalls());
        assertEquals(11, method.getForeignCalls());
    }

    @Test
    void reportFormatVersionSurvivesARoundTrip() throws Exception {
        Module reloaded = roundTrip(moduleWithWeightedReferences());

        assertEquals(Module.CURRENT_REPORT_FORMAT_VERSION, reloaded.getReportFormatVersion());
        assertTrue(reloaded.isReportFormatCurrent());
    }

    @Test
    void reportWithoutAFormatVersionReadsAsOutdated() throws Exception {
        Path reportFile = tempDir.resolve("Java/Java_old.json");
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, """
                {
                  "name" : "old",
                  "packages" : [ ]
                }
                """);

        Module reloaded = objectMapper.readValue(reportFile.toFile(), Module.class);

        assertEquals(0, reloaded.getReportFormatVersion());
        assertFalse(reloaded.isReportFormatCurrent(),
                "a report without a format version must not be mistaken for a current one");
    }

    @Test
    void reportWithoutTheWeightedRecordReadsAsEmptyButKeepsTheImports() throws Exception {
        Path reportFile = tempDir.resolve("Java/Java_old.json");
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, """
                {
                  "name" : "old",
                  "packages" : [ {
                    "name" : "demo",
                    "buildUnits" : [ {
                      "name" : "demo.Source",
                      "production" : true,
                      "generated" : false,
                      "imports" : [ "demo.Target" ],
                      "classes" : [ ]
                    } ]
                  } ]
                }
                """);

        Module reloaded = objectMapper.readValue(reportFile.toFile(), Module.class);
        BuildUnit buildUnit = firstBuildUnit(reloaded);

        assertTrue(buildUnit.getReferences().isEmpty(),
                "a report written before the weighted record has no references to offer");
        assertEquals(List.of("demo.Target"), buildUnit.getImports(),
                "the flat import list of an older report must still be readable for the existing metric tools");
    }
}
