package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four Java metric tools that predate the weighted reference record consume the flat list of referenced
 * type names. Existing workspaces hold reports written before the record existed, and those reports are
 * reused rather than re-parsed, so the record must not become a precondition for a correct answer: a report
 * without it has to produce exactly the same reports as one with it.
 */
class ExistingMetricReportCompatibilityTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private Module module(String name, String ownClass, String importedClass, boolean withReferences) {
        Method method = new Method("run", "()V");
        Clazz clazz = new Clazz(ownClass, null);
        clazz.addMethod(method);

        BuildUnit buildUnit = new BuildUnit(name + ".Main", true, false,
                List.of(importedClass, "java.util.List", "demo.thirdparty.Helper"),
                withReferences ? List.of(new ClassReference(importedClass, 3, 7, false)) : List.of(),
                List.of(clazz));

        Package pck = new Package(ownClass.substring(0, ownClass.lastIndexOf('.')));
        pck.addBuildUnit(buildUnit);

        Module module = new Module(name);
        module.addPackage(pck);

        return module;
    }

    private AnalysisContext writeWorkspace(boolean withReferences) throws Exception {
        Module core = module("core", "demo.core.Service", "demo.api.Widget", withReferences);
        Module api = module("api", "demo.api.Widget", "demo.core.Service", withReferences);

        Path javaDirectory = tempDir.resolve("Java");
        Files.createDirectories(javaDirectory);
        objectMapper.writeValue(javaDirectory.resolve("Java_core.json").toFile(), core);
        objectMapper.writeValue(javaDirectory.resolve("Java_api.json").toFile(), api);

        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : List.of("core", "api")) {
            ObjectNode moduleNode = modules.addObject();
            moduleNode.put("name", moduleName);
            moduleNode.put("path", ".");

            ArrayNode languages = moduleNode.putObject("programmingLanguages").putArray("programmingLanguages");
            ObjectNode language = languages.addObject();
            language.put("name", "Java");
            language.put("version", "unknown");
        }

        return new AnalysisContext(tempDir, tempDir, Map.of(), state);
    }

    private String reportsOfTheFourTools(boolean withReferences) throws Exception {
        JavaTool javaTool = new JavaTool(writeWorkspace(withReferences));

        StringBuilder reports = new StringBuilder();

        reports.append("coupling=")
                .append(objectMapper.writeValueAsString(javaTool.getCouplingReport("core"))).append('\n');
        reports.append("packageTangles=")
                .append(objectMapper.writeValueAsString(javaTool.getPackageTangleReport("core"))).append('\n');
        reports.append("importDiversity=")
                .append(objectMapper.writeValueAsString(javaTool.getImportDiversityReport("core"))).append('\n');
        reports.append("interModule=")
                .append(objectMapper.writeValueAsString(javaTool.getInterModuleDependencyReport())).append('\n');

        return reports.toString();
    }

    @Test
    void theWeightedReferenceRecordDoesNotChangeTheExistingReports() throws Exception {
        String withoutRecord = reportsOfTheFourTools(false);
        String withRecord = reportsOfTheFourTools(true);

        assertEquals(withoutRecord, withRecord,
                "the weighted reference record must not become a precondition for the existing metric tools");
    }

    @Test
    void theFixtureActuallyProducesFindings() throws Exception {
        String reports = reportsOfTheFourTools(true);

        assertTrue(reports.contains("Efferent coupling production code"),
                "the coupling report must be present, got: " + reports);
        assertTrue(reports.contains("Import distribution production code"),
                "the import diversity report must be present, got: " + reports);
        assertTrue(reports.contains("Instability"),
                "the inter-module report must be present, got: " + reports);
        assertTrue(reports.contains("Module dependencies production code"),
                "the coupling report must carry the module level distributions, got: " + reports);
    }
}
