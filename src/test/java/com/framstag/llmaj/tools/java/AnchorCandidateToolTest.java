package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.ToolFactory;
import com.framstag.llmaj.tools.sbom.SBOMTool;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anchor candidate tool is the bound of the modernisation anchor analysis: the analysis must read a payload
 * whose size is set by a workspace property and not by the number of modules of the project. So the tool has to
 * return the strongest candidates of every anchor type up to the budget, state how many candidates the type
 * holds beside the bounded list, and carry the counted evidence and the analysis that counted it with every
 * candidate. A module whose report is missing carries no evidence and is named as skipped instead.
 */
class AnchorCandidateToolTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static BuildUnit buildUnit(String name,
                                       List<String> imports,
                                       List<ClassReference> references,
                                       List<Clazz> classes) {
        return new BuildUnit(name, true, false, imports, references, classes);
    }

    private static Clazz interfaceClass(String name) {
        Clazz clazz = new Clazz(name, null);
        clazz.setInterface(true);

        return clazz;
    }

    private static Clazz classWithInterface(String name, String... interfaces) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.addMethod(new Method("run", "()V"));

        for (String implemented : interfaces) {
            clazz.addInterface(implemented);
        }

        return clazz;
    }

    private static Clazz plainClass(String name) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.addMethod(new Method("run", "()V"));

        return clazz;
    }

    /**
     * Twenty interfaces in one module. The port has two implementors and is imported by the other module, so it
     * is the strongest seam of the project; the other nineteen interfaces nobody implements are weaker. The
     * module has more seam candidates than the seeded budget of fifteen.
     */
    private static Module seamModule() {
        return seamModule("seams");
    }

    private static Module seamModule(String moduleName) {
        Package pck = new Package("demo.seams");

        pck.addBuildUnit(buildUnit("demo.seams.Port",
                List.of(),
                List.of(),
                List.of(interfaceClass("demo.seams.Port"))));

        for (int index = 0; index < 19; index++) {
            String name = String.format("demo.seams.Port%02d", index);

            pck.addBuildUnit(buildUnit(name, List.of(), List.of(), List.of(interfaceClass(name))));
        }

        pck.addBuildUnit(buildUnit("demo.seams.PortImplA",
                List.of("demo.seams.Port"),
                List.of(new ClassReference("demo.seams.Port", 0, 0, true)),
                List.of(classWithInterface("demo.seams.PortImplA", "demo.seams.Port"))));

        pck.addBuildUnit(buildUnit("demo.seams.PortImplB",
                List.of("demo.seams.Port"),
                List.of(new ClassReference("demo.seams.Port", 0, 0, true)),
                List.of(classWithInterface("demo.seams.PortImplB", "demo.seams.Port"))));

        Module module = new Module(moduleName);
        module.addPackage(pck);

        return module;
    }

    /**
     * Four classes in two packages with one heavy tie between the packages, so that the ladder of the module
     * holds exactly two steps. One of the classes imports the port of the seam module, which makes the seam
     * module the hub of the inter module dependency matrix.
     */
    private static Module coreModule() {
        Package core = new Package("demo.core");

        core.addBuildUnit(buildUnit("demo.core.Model",
                List.of("demo.seams.Port"),
                List.of(new ClassReference("demo.api.Dto", 9, 9, false),
                        new ClassReference("demo.core.Service", 1, 1, false)),
                List.of(plainClass("demo.core.Model"))));

        core.addBuildUnit(buildUnit("demo.core.Service",
                List.of(),
                List.of(new ClassReference("demo.core.Repository", 1, 1, false)),
                List.of(plainClass("demo.core.Service"))));

        core.addBuildUnit(buildUnit("demo.core.Repository",
                List.of(),
                List.of(new ClassReference("demo.api.Dto", 1, 1, false)),
                List.of(plainClass("demo.core.Repository"))));

        Package api = new Package("demo.api");
        api.addBuildUnit(buildUnit("demo.api.Dto", List.of(), List.of(), List.of(plainClass("demo.api.Dto"))));

        Module module = new Module("core");
        module.addPackage(core);
        module.addPackage(api);

        return module;
    }

    private void writeReport(String moduleName, Module module) throws IOException {
        Path reportDirectory = tempDir.resolve("Java");
        Files.createDirectories(reportDirectory);
        objectMapper.writeValue(reportDirectory.resolve("Java_" + moduleName + ".json").toFile(), module);
    }

    private AnalysisContext contextOf(Map<String, String> properties, String... moduleNames) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : moduleNames) {
            ObjectNode module = modules.addObject();
            module.put("name", moduleName);
            module.put("path", ".");

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            languages.addObject().put("name", "Java").put("version", "unknown");
        }

        return new AnalysisContext(tempDir, tempDir, properties, state);
    }

    private AnchorCandidateTool toolWith(Map<String, String> properties, String... moduleNames) throws IOException {
        writeReport("core", coreModule());
        writeReport("seams", seamModule());

        AnalysisContext context = contextOf(properties, moduleNames);

        return new AnchorCandidateTool(context, new JavaTool(context), new SBOMTool(context));
    }

    private AnchorCandidateTool tool(String... moduleNames) throws IOException {
        return toolWith(Map.of(), moduleNames);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> anchorTypeOf(Map<String, Object> result, String anchorType) {
        return ((List<Map<String, Object>>) result.get("anchorTypes")).stream()
                .filter(entry -> anchorType.equals(entry.get("anchorType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no anchor type " + anchorType
                        + ", got: " + result.get("anchorTypes")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> candidatesOf(Map<String, Object> result, String anchorType) {
        return (List<Map<String, Object>>) anchorTypeOf(result, anchorType).get("candidates");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> skippedOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("skipped");
    }

    @Test
    void aProjectWithMoreSeamCandidatesThanTheBudgetReturnsTheStrongestAndStatesTheTotal() throws Exception {
        Map<String, Object> result = tool("core", "seams").getAnchorCandidates();

        Map<String, Object> seams = anchorTypeOf(result, AnchorCandidateTool.SEAM_ANCHOR_TYPE);

        assertEquals(Config.getBudget(Map.of(), Config.ANCHOR_CANDIDATES_PER_TYPE_PROPERTY,
                        AnchorCandidateTool.DEFAULT_CANDIDATES_PER_TYPE),
                seams.get("budget"), "a workspace that names no budget is bounded by the seeded value");
        assertEquals(20, seams.get("totalCount"), "the project holds twenty interfaces");
        assertEquals(15, seams.get("returnedCount"), "the list is bounded by the seeded budget");
        assertEquals(15, candidatesOf(result, AnchorCandidateTool.SEAM_ANCHOR_TYPE).size());
        assertEquals("demo.seams.Port",
                candidatesOf(result, AnchorCandidateTool.SEAM_ANCHOR_TYPE).getFirst().get("element"),
                "the port with two implementors and two importing modules is the strongest seam");
        assertFalse(candidatesOf(result, AnchorCandidateTool.SEAM_ANCHOR_TYPE).stream()
                        .anyMatch(candidate -> "demo.seams.Port18".equals(candidate.get("element"))),
                "the weakest interface does not fit under the budget");
    }

    @Test
    void aProjectWithFewerCutPointCandidatesThanTheBudgetReturnsAllOfThem() throws Exception {
        Map<String, Object> result = tool("core", "seams").getAnchorCandidates();

        Map<String, Object> cutPoints = anchorTypeOf(result, AnchorCandidateTool.CUT_POINT_ANCHOR_TYPE);
        List<Map<String, Object>> candidates = candidatesOf(result,
                AnchorCandidateTool.CUT_POINT_ANCHOR_TYPE);

        assertEquals(2, cutPoints.get("totalCount"), "the ladder of the core module holds two steps");
        assertEquals(2, candidates.size(), "every cut point fits under the budget");
        assertEquals(cutPoints.get("totalCount"), cutPoints.get("returnedCount"),
                "a list that holds every candidate states the same number twice");
        assertEquals("core step 1", candidates.getFirst().get("element"),
                "the cheapest step of the ladder comes first");
    }

    @Test
    void aCandidateNamesItsEvidenceAndItsSourceAnalysis() throws Exception {
        Map<String, Object> result = tool("core", "seams").getAnchorCandidates();

        Map<String, Object> port = candidatesOf(result, AnchorCandidateTool.SEAM_ANCHOR_TYPE).getFirst();

        assertEquals(AnchorCandidateTool.SEAM_ANCHOR_TYPE,
                anchorTypeOf(result, AnchorCandidateTool.SEAM_ANCHOR_TYPE).get("anchorType"));
        assertEquals("seams", port.get("module"));
        assertEquals("java_get_seams", port.get("source"),
                "the candidate names the analysis that counted its evidence");

        @SuppressWarnings("unchecked")
        List<String> evidence = (List<String>) port.get("evidence");

        assertFalse(evidence.isEmpty(), "a candidate without evidence is no anchor");
        assertTrue(evidence.stream().anyMatch(entry -> entry.startsWith("implemented 2 time(s)")),
                "the evidence carries the counted number, got: " + evidence);
        assertTrue(evidence.stream().anyMatch(entry -> entry.contains("imported by 2 module(s)")), evidence.toString());
        assertTrue(evidence.stream().anyMatch(entry -> entry.contains("demo.seams.PortImplA (seams)")),
                "the evidence names what it counted, got: " + evidence);
    }

    @Test
    void aModuleWithoutAReportIsSkippedAndNamedWithItsReason() throws Exception {
        Map<String, Object> result = tool("core", "seams", "missing").getAnchorCandidates();

        List<Map<String, Object>> skipped = skippedOf(result);

        assertEquals(1, skipped.size(), "the same module is named once, however many analyses miss its report");
        assertEquals("missing", skipped.getFirst().get("moduleName"));
        assertTrue(((String) skipped.getFirst().get("reason")).contains("No raw Java module report"),
                skipped.toString());

        assertFalse(candidatesOf(result, AnchorCandidateTool.BUILD_UNIT_ANCHOR_TYPE).stream()
                        .anyMatch(candidate -> "missing".equals(candidate.get("module"))),
                "a module without a report holds no candidate, its zeroes are a gap and not a count");
    }

    @Test
    void theBudgetComesFromTheWorkspace() throws Exception {
        Map<String, Object> tight = toolWith(Map.of(Config.ANCHOR_CANDIDATES_PER_TYPE_PROPERTY, "3"),
                "core", "seams").getAnchorCandidates();
        Map<String, Object> wide = toolWith(Map.of(Config.ANCHOR_CANDIDATES_PER_TYPE_PROPERTY, "100"),
                "core", "seams").getAnchorCandidates();

        assertEquals(3, tight.get("candidatesPerType"));
        assertEquals(3, candidatesOf(tight, AnchorCandidateTool.SEAM_ANCHOR_TYPE).size());
        assertEquals(20, anchorTypeOf(tight, AnchorCandidateTool.SEAM_ANCHOR_TYPE).get("totalCount"),
                "the total of the type does not depend on the budget");

        assertEquals(100, wide.get("candidatesPerType"));
        assertEquals(20, candidatesOf(wide, AnchorCandidateTool.SEAM_ANCHOR_TYPE).size(),
                "a budget above the number of candidates returns all of them");
    }

    @Test
    void everyToolBackedAnchorTypeIsReported() throws Exception {
        Map<String, Object> result = tool("core", "seams").getAnchorCandidates();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> anchorTypes = (List<Map<String, Object>>) result.get("anchorTypes");

        assertEquals(List.of("entry-point", "seam", "build-unit", "cut-point", "test-coverage",
                        "technology-marker"),
                anchorTypes.stream().map(entry -> entry.get("anchorType")).toList(),
                "the six anchor types that rest on tool evidence are reported, the domain anchors are not");

        for (Map<String, Object> anchorType : anchorTypes) {
            for (String key : List.of("anchorType", "budget", "totalCount", "candidates")) {
                assertTrue(anchorType.containsKey(key),
                        "the result of the type " + anchorType.get("anchorType") + " states " + key);
            }
        }
    }

    @Test
    void aMissingSbomIsAnAbsentSourceAndNotAnEmptyDependencyList() throws Exception {
        Map<String, Object> result = tool("core", "seams").getAnchorCandidates();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> absentSources = (List<Map<String, Object>>) result.get("absentSources");

        assertEquals(1, absentSources.size());
        assertEquals("sbom_get_application_dependencies", absentSources.getFirst().get("source"));
        assertTrue(((String) absentSources.getFirst().get("reason")).contains("SBOM is not loaded"),
                absentSources.toString());

        assertTrue(candidatesOf(result, AnchorCandidateTool.TECHNOLOGY_MARKER_ANCHOR_TYPE).isEmpty(),
                "the project imports no namespace outside its own modules, so there is no namespace evidence");
    }

    /**
     * The technology markers rest on the consolidated namespace evidence, which removes the entries that name a type,
     * an array descriptor and the project itself. Those entries used to reach the candidate list and spend its budget.
     */
    @Test
    void theTechnologyMarkersRestOnTheConsolidatedNamespaceEvidence() throws Exception {
        Module library = moduleWithImports("lib", "demo.lib", List.of(
                "org.slf4j.Logger",
                "picocli.CommandLine",
                "[Lorg.example.Foo",
                "demo.lib.Model"));

        writeReport("lib", library);

        AnalysisContext context = contextOf(Map.of(), "lib");
        AnchorCandidateTool tool = new AnchorCandidateTool(context, new JavaTool(context), new SBOMTool(context));

        Map<String, Object> result = tool.getAnchorCandidates();
        List<Map<String, Object>> candidates = candidatesOf(result, AnchorCandidateTool.TECHNOLOGY_MARKER_ANCHOR_TYPE);

        assertEquals(List.of("org.slf4j"), candidates.stream().map(candidate -> candidate.get("element")).toList(),
                "the ordinary namespace is the only evidence, got: " + candidates);
        assertEquals("java_get_import_technology_evidence", candidates.getFirst().get("source"),
                "the candidate names the evidence that counted the namespace");

        for (Map<String, Object> candidate : candidates) {
            assertTrue(!String.valueOf(candidate.get("element")).startsWith("["),
                    "an array descriptor is no candidate: " + candidate);
            assertTrue(!String.valueOf(candidate.get("element")).startsWith("demo."),
                    "the namespace of the project is no candidate: " + candidate);
            assertTrue(!String.valueOf(candidate.get("element")).contains("CommandLine"),
                    "an entry that names a type is no candidate: " + candidate);
        }
    }

    private static Module moduleWithImports(String moduleName, String packageName, List<String> imports) {
        Package pck = new Package(packageName);

        pck.addBuildUnit(buildUnit(packageName + ".Service", imports, List.of(),
                List.of(plainClass(packageName + ".Service"))));

        Module module = new Module(moduleName);
        module.addPackage(pck);

        return module;
    }

    @Test
    void theToolIsRegisteredInTheToolFactory() {
        List<Object> tools = ToolFactory.getToolInstanceList(contextOf(Map.of(), "core"));

        assertTrue(tools.stream().anyMatch(tool -> tool instanceof AnchorCandidateTool),
                "the anchor candidate tool has to be part of the tool list of the engine");
    }

    @Test
    void anElementThatSeveralModuleReportsHoldIsReportedOnceWithItsModules() throws Exception {
        // Three module reports hold the same interface, as an aggregator module and its inner modules do.
        writeReport("alpha", portModule("alpha"));
        writeReport("beta", portModule("beta"));

        AnalysisContext context = contextOf(Map.of(), "alpha", "beta", "core");
        AnchorCandidateTool anchorCandidateTool = new AnchorCandidateTool(context, new JavaTool(context),
                new SBOMTool(context));

        Map<String, Object> result = anchorCandidateTool.getAnchorCandidates();
        List<Map<String, Object>> seams = candidatesOf(result, AnchorCandidateTool.SEAM_ANCHOR_TYPE);

        List<Map<String, Object>> occurrences = seams.stream()
                .filter(candidate -> "demo.ports.Port".equals(candidate.get("element")))
                .toList();

        assertEquals(1, occurrences.size(), "the element is reported once, not once per module report");

        Map<String, Object> candidate = occurrences.getFirst();
        List<?> alsoSeenIn = (List<?>) candidate.get("alsoSeenIn");

        assertEquals(1, alsoSeenIn.size(), "the candidate names the module it was seen in beside its own");
        assertTrue(String.join(" ", strings(candidate.get("evidence"))).contains("hold the same element"),
                "the evidence states that another module report holds the same element");
    }

    @Test
    void theRankAfterAFoldedElementIsNotPushedDown() throws Exception {
        writeReport("alpha", seamModule("alpha"));
        writeReport("beta", seamModule("beta"));

        AnalysisContext context = contextOf(Map.of(), "alpha", "beta", "core");
        AnchorCandidateTool anchorCandidateTool = new AnchorCandidateTool(context, new JavaTool(context),
                new SBOMTool(context));

        List<Map<String, Object>> seams = candidatesOf(anchorCandidateTool.getAnchorCandidates(),
                AnchorCandidateTool.SEAM_ANCHOR_TYPE);

        assertTrue(seams.size() > 1, "the project holds more than one seam");
        assertNotEquals(seams.get(0).get("element"), seams.get(1).get("element"),
                "the second rank holds the next seam, not a second view of the first");
    }

    @Test
    void theProductionEntryPointRanksBeforeTheBenchmarkHarness() throws Exception {
        writeReport("app", entryPointModule("app", true, "demo.app.Launcher", 0));
        writeReport("bench", entryPointModule("bench", false, "demo.bench.Harness", 6));

        AnalysisContext context = contextOf(Map.of(), "app", "bench");
        AnchorCandidateTool anchorCandidateTool = new AnchorCandidateTool(context, new JavaTool(context),
                new SBOMTool(context));

        List<Map<String, Object>> entryPoints = candidatesOf(anchorCandidateTool.getAnchorCandidates(),
                AnchorCandidateTool.ENTRY_POINT_ANCHOR_TYPE);

        assertEquals("demo.app.Launcher", entryPoints.getFirst().get("element"),
                "the production main method ranks before the benchmark main method");
        assertTrue(entryPoints.stream().anyMatch(candidate -> "demo.bench.Harness".equals(candidate.get("element"))),
                "a test entry point stays in the list, below the production ones");
        assertTrue(String.join(" ", strings(entryPoints.getFirst().get("evidence"))).contains("production code"),
                "the evidence of a candidate names its code category");
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }

        return List.of();
    }

    private static Module portModule(String moduleName) {
        Package ports = new Package("demo.ports");
        ports.addBuildUnit(buildUnit("demo.ports.Port", List.of(), List.of(),
                List.of(interfaceClass("demo.ports.Port"))));

        Module module = new Module(moduleName);
        module.addPackage(ports);

        return module;
    }

    private static Module entryPointModule(String moduleName, boolean production, String mainClass, int annotations) {
        Clazz clazz = new Clazz(mainClass, null);
        clazz.setSuperClass("java.lang.Object");

        Method main = new Method("main", "()V");
        main.setStatic(true);
        clazz.addMethod(main);

        for (int index = 0; index < annotations; index++) {
            clazz.addAnnotation(new Annotation("Annotation" + index, "demo.Annotation" + index));
        }

        Package pck = new Package("demo.app");
        pck.addBuildUnit(new BuildUnit("demo.app", production, false, List.of(), List.of(), List.of(clazz)));

        Module module = new Module(moduleName);
        module.addPackage(pck);

        return module;
    }

    @Test
    void theToolIsNamedInTheJavaFamily() throws Exception {
        java.lang.reflect.Method method = AnchorCandidateTool.class.getMethod("getAnchorCandidates");

        Tool tool = method.getAnnotation(Tool.class);

        assertNotNull(tool, "the anchor candidate tool has to be a tool of the engine");
        assertEquals("java_get_anchor_candidates", tool.name());
        assertFalse(String.join(" ", tool.value()).isBlank(), "the tool has to tell the model what it returns");
    }
}
