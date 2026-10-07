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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The graph is the structure inside one module, so it holds the module's own classes and nothing else. A
 * reference to a type the module does not define is coupling to the outside world, and a class from a shaded
 * dependency package is not a class of the module at all. Both are excluded, and both are counted, so the
 * omission is visible instead of silent.
 */
class ClassDependencyGraphToolTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static BuildUnit buildUnit(String name, ClassReference... references) {
        Clazz clazz = new Clazz(name, null);
        clazz.addMethod(new Method("run", "()V"));

        return new BuildUnit(name, true, false, List.of(), List.of(references), List.of(clazz));
    }

    /**
     * Ten classes of the module under demo, and one class of a shaded dependency under com.shaded. A shaded
     * dependency is a small minority of the classes of a module, which is what the namespace rule rests on.
     */
    private static Module fixtureModule() {
        Package core = new Package("demo.core");
        core.addBuildUnit(buildUnit("demo.core.Service",
                new ClassReference("demo.core.Repository", 2, 5, false),
                new ClassReference("demo.core.Model", 0, 0, true),
                new ClassReference("org.external.Widget", 1, 3, false),
                new ClassReference("demo.core.Service", 4, 9, false)));
        core.addBuildUnit(buildUnit("demo.core.Repository",
                new ClassReference("demo.core.Service", 1, 2, false)));
        core.addBuildUnit(buildUnit("demo.core.Model"));
        core.addBuildUnit(buildUnit("demo.core.Mapper"));
        core.addBuildUnit(buildUnit("demo.core.Validator"));

        Package api = new Package("demo.api");
        api.addBuildUnit(buildUnit("demo.api.Controller",
                new ClassReference("demo.core.Service", 3, 4, false)));
        api.addBuildUnit(buildUnit("demo.api.Dto"));
        api.addBuildUnit(buildUnit("demo.api.Request"));
        api.addBuildUnit(buildUnit("demo.api.Response"));
        api.addBuildUnit(buildUnit("demo.api.Mapper"));

        Package shaded = new Package("com.shaded.dep");
        shaded.addBuildUnit(buildUnit("com.shaded.dep.Foreign",
                new ClassReference("demo.core.Service", 1, 1, false)));

        Module module = new Module("core");
        module.addPackage(core);
        module.addPackage(api);
        module.addPackage(shaded);

        return module;
    }

    private void writeReport(String moduleName, Module module) throws Exception {
        Files.createDirectories(tempDir.resolve("Java"));
        objectMapper.writeValue(tempDir.resolve("Java/Java_" + moduleName + ".json").toFile(), module);
    }

    private void writeOutdatedReport(String moduleName) throws Exception {
        Files.createDirectories(tempDir.resolve("Java"));
        Files.writeString(tempDir.resolve("Java/Java_" + moduleName + ".json"), """
                {
                  "name" : "%s",
                  "packages" : [ ]
                }
                """.formatted(moduleName));
    }

    private JavaTool javaTool() {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        ObjectNode module = modules.addObject();
        module.put("name", "core");
        module.put("path", ".");

        ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
        languages.addObject().put("name", "Java").put("version", "unknown");

        return new JavaTool(new AnalysisContext(tempDir, tempDir, Map.of(), state));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edges(Map<String, Object> graph, String key) {
        return (List<Map<String, Object>>) graph.get(key);
    }

    private static Map<String, Object> edgeBetween(List<Map<String, Object>> edges, String from, String to) {
        return edges.stream()
                .filter(edge -> from.equals(edge.get("from")) && to.equals(edge.get("to")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no edge from " + from + " to " + to + " in " + edges));
    }

    @Test
    void theGraphHoldsTheModulesClassesAndTheirWeights() throws Exception {
        writeReport("core", fixtureModule());

        Map<String, Object> graph = javaTool().getClassDependencyGraph("core");

        assertEquals("OK", graph.get("status"));
        assertEquals("demo", graph.get("projectNamespace"));
        assertEquals(10, graph.get("nodeCount"));
        assertEquals(2, graph.get("referenceEdgeCount"));
        assertEquals(1, graph.get("structuralEdgeCount"));

        List<Map<String, Object>> referenceEdges = edges(graph, "referenceEdges");
        Map<String, Object> serviceToRepository = edgeBetween(referenceEdges,
                "demo.core.Repository", "demo.core.Service");

        assertEquals(3, serviceToRepository.get("apiWidth"),
                "two members used one way and one the other way are three members of the interface");
        assertEquals(7, serviceToRepository.get("traffic"),
                "five reference sites one way and two the other way are seven sites");
    }

    @Test
    void bothDirectionsOfACouplingAreFoldedIntoOneEdge() throws Exception {
        writeReport("core", fixtureModule());

        Map<String, Object> graph = javaTool().getClassDependencyGraph("core");

        assertEquals(2, ((List<?>) graph.get("referenceEdges")).size(),
                "the graph is undirected, so a pair that references each other is one edge");
    }

    @Test
    void aStructuralRelationIsListedButIsNotAReferenceEdge() throws Exception {
        writeReport("core", fixtureModule());

        Map<String, Object> graph = javaTool().getClassDependencyGraph("core");

        assertEquals(List.of(Map.of("from", "demo.core.Model", "to", "demo.core.Service",
                        "apiWidth", 0, "traffic", 0)),
                edges(graph, "structuralEdges"),
                "a declared field type is a structural relation with no reference site of its own");

        assertTrue(edges(graph, "referenceEdges").stream()
                        .noneMatch(edge -> edge.get("from").equals("demo.core.Model")),
                "a structural relation must not appear among the weighted reference edges");
    }

    @Test
    void aShadedDependencyClassIsNeitherANodeNorAnEdgeTarget() throws Exception {
        writeReport("core", fixtureModule());

        Map<String, Object> graph = javaTool().getClassDependencyGraph("core");

        assertTrue(((List<Map<String, Object>>) graph.get("nodes")).stream()
                        .noneMatch(node -> node.get("name").equals("com.shaded.dep.Foreign")),
                "a class outside the project namespace is not a node of the module");

        assertTrue(edges(graph, "referenceEdges").stream()
                        .noneMatch(edge -> edge.get("from").toString().startsWith("com.shaded")
                                || edge.get("to").toString().startsWith("com.shaded")),
                "a class outside the project namespace is not an edge target either");
    }

    @Test
    void theExclusionsAreCounted() throws Exception {
        writeReport("core", fixtureModule());

        Map<String, Object> graph = javaTool().getClassDependencyGraph("core");

        assertEquals(1, graph.get("excludedNodeCount"),
                "the shaded dependency class is one excluded node");
        assertEquals(1, graph.get("excludedEdgeCount"),
                "the reference to the type the module does not define is one excluded reference");
    }

    @Test
    void aReferenceInsideTheSameClassGroupIsNotAnEdge() throws Exception {
        writeReport("core", fixtureModule());

        Map<String, Object> graph = javaTool().getClassDependencyGraph("core");

        assertTrue(edges(graph, "referenceEdges").stream()
                        .noneMatch(edge -> edge.get("from").equals(edge.get("to"))),
                "a class referencing itself is not a coupling between two classes");
        assertEquals(1, graph.get("excludedEdgeCount"),
                "a self reference is not an exclusion either, it is simply not an edge");
    }

    @Test
    void anOutdatedReportIsReportedInsteadOfAnEmptyGraph() throws Exception {
        writeOutdatedReport("core");

        Map<String, Object> graph = javaTool().getClassDependencyGraph("core");

        assertEquals("NOT_AVAILABLE", graph.get("status"));
        assertFalse(graph.containsKey("nodes"),
                "a report without the weighted record must not be presented as a module without references");

        String reasoning = (String) graph.get("reasoning");
        assertTrue(reasoning.contains("no report format version"),
                "the reason must name the missing version, got: " + reasoning);
        assertTrue(reasoning.contains("Regenerate"),
                "the reason must name the regeneration step, got: " + reasoning);
    }

    @Test
    void theBatchToolReturnsASummaryAndNotEveryEdge() throws Exception {
        writeReport("core", fixtureModule());

        JavaTool javaTool = javaTool();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) javaTool
                .getAllClassDependencyGraphs().get("reports");

        assertEquals(1, reports.size());
        Map<String, Object> summary = reports.getFirst();

        assertEquals(10, summary.get("nodeCount"));
        assertEquals(2, summary.get("referenceEdgeCount"));
        assertTrue(summary.containsKey("strongestCouplings"));
        assertFalse(summary.containsKey("referenceEdges"),
                "a large project would not fit into a conversation if every module returned its full edge list");
    }

    @Test
    void theBatchToolSkipsAnOutdatedReport() throws Exception {
        writeOutdatedReport("core");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) javaTool()
                .getAllClassDependencyGraphs().get("skipped");

        assertEquals(1, skipped.size());
        assertEquals("NOT_AVAILABLE", skipped.getFirst().get("status"));
    }
}
