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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ladder answers "where does this module come apart". One heavy tie means two sides are not independent,
 * however few ties there are, so the step that severs a heavy tie has to appear at its own weight and not
 * before the cheap steps. A separation is allowed to cut across package boundaries, which is the only way it
 * can say something the package tree does not already say.
 */
class SplitCandidateToolTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static BuildUnit buildUnit(String name, ClassReference... references) {
        Clazz clazz = new Clazz(name, null);
        clazz.addMethod(new Method("run", "()V"));

        return new BuildUnit(name, true, false, List.of(), List.of(references), List.of(clazz));
    }

    /**
     * Four classes in two packages, held together by one heavy tie between the packages and three light ties:
     *
     * <pre>
     *   core.Model --9-- api.Dto
     *     |  1             1  |
     *   core.Service -1- core.Repository
     * </pre>
     */
    private static Module crossPackageModule() {
        Package core = new Package("demo.core");
        core.addBuildUnit(buildUnit("demo.core.Model",
                new ClassReference("demo.api.Dto", 9, 9, false),
                new ClassReference("demo.core.Service", 1, 1, false)));
        core.addBuildUnit(buildUnit("demo.core.Service",
                new ClassReference("demo.core.Repository", 1, 1, false)));
        core.addBuildUnit(buildUnit("demo.core.Repository",
                new ClassReference("demo.api.Dto", 1, 1, false)));

        Package api = new Package("demo.api");
        api.addBuildUnit(buildUnit("demo.api.Dto"));

        Module module = new Module("core");
        module.addPackage(core);
        module.addPackage(api);

        return module;
    }

    /** A chain of classes, so that the module is far above the limit for the exact minimum cut. */
    private static Module chainModule(int classCount) {
        Package demo = new Package("demo");

        for (int index = 0; index < classCount; index++) {
            List<ClassReference> references = index + 1 < classCount
                    ? List.of(new ClassReference("demo.C" + (index + 1), 1, 1, false))
                    : List.of();

            demo.addBuildUnit(buildUnit("demo.C" + index, references.toArray(ClassReference[]::new)));
        }

        Module module = new Module("core");
        module.addPackage(demo);

        return module;
    }

    private JavaTool javaTool(Module module) throws Exception {
        Files.createDirectories(tempDir.resolve("Java"));
        objectMapper.writeValue(tempDir.resolve("Java/Java_core.json").toFile(), module);

        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");
        ObjectNode moduleNode = modules.addObject();
        moduleNode.put("name", "core");
        moduleNode.put("path", ".");

        ArrayNode languages = moduleNode.putObject("programmingLanguages").putArray("programmingLanguages");
        languages.addObject().put("name", "Java").put("version", "unknown");

        return new JavaTool(new AnalysisContext(tempDir, tempDir, Map.of(), state));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> separations(Map<String, Object> descriptor) {
        return (List<Map<String, Object>>) descriptor.get("separations");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(Map<String, Object> separation) {
        return (List<Map<String, Object>>) separation.get("groups");
    }

    private Map<String, Object> splitCandidates(Module module) throws Exception {
        return javaTool(module).getSplitCandidates("core");
    }

    @Test
    void theLadderRisesInCostAndTheCheapestStepComesFirst() throws Exception {
        Map<String, Object> descriptor = splitCandidates(crossPackageModule());

        assertEquals("OK", descriptor.get("status"));
        assertEquals(4, descriptor.get("nodeCount"));
        assertEquals(2, descriptor.get("separationCount"));

        assertEquals(List.of(1, 9), separations(descriptor).stream()
                .map(separation -> separation.get("cost"))
                .toList());
    }

    @Test
    void aHeavyTieIsPricedAtItsOwnWeight() throws Exception {
        Map<String, Object> lastStep = separations(splitCandidates(crossPackageModule())).getLast();

        assertEquals(9, lastStep.get("cost"));
        assertEquals(9, lastStep.get("strongestSeveredWeight"),
                "the heavy tie between the two classes is the strongest tie the step severs");
        assertEquals(1, lastStep.get("weakestSeveredWeight"),
                "the light ties that are already severed are still reported");
        assertEquals(12, lastStep.get("totalSeveredWeight"));
        assertEquals(4, lastStep.get("groupCount"),
                "severing the heavy tie leaves every class on its own");
    }

    @Test
    void aSeparationNamesTheEdgesItSevers() throws Exception {
        Map<String, Object> firstStep = separations(splitCandidates(crossPackageModule())).getFirst();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> severedEdges = (List<Map<String, Object>>) firstStep.get("severedEdges");

        assertFalse(severedEdges.isEmpty(), "a separation has to name the ties it severs");

        for (Map<String, Object> edge : severedEdges) {
            assertTrue(edge.containsKey("from") && edge.containsKey("to") && edge.containsKey("weight"));
        }

        assertTrue(severedEdges.stream().anyMatch(edge -> edge.get("weight").equals(1)));
    }

    @Test
    void aCheapestSeparationMayCutAcrossPackages() throws Exception {
        Map<String, Object> group = groups(separations(splitCandidates(crossPackageModule())).getFirst())
                .stream()
                .filter(candidate -> candidate.get("size").equals(2))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the cheapest step has to hold the cross package group"));

        assertEquals(2, group.get("size"));
        assertEquals(1, group.get("dominantPackageMemberCount"));
        assertEquals(1, group.get("nonConformingMemberCount"),
                "a group that spans two packages has to say how many of its members do not fit the dominant one");

        @SuppressWarnings("unchecked")
        List<String> members = (List<String>) group.get("members");

        assertTrue(members.contains("demo.core.Model") && members.contains("demo.api.Dto"),
                "the group spans demo.core and demo.api, got: " + members);
    }

    @Test
    void theGroupNamesItsDominantPackage() throws Exception {
        Map<String, Object> group = groups(separations(splitCandidates(crossPackageModule())).getFirst())
                .stream()
                .filter(candidate -> candidate.get("size").equals(1))
                .findFirst()
                .orElseThrow();

        String dominantPackage = (String) group.get("dominantPackage");

        assertTrue(dominantPackage.startsWith("demo."), "got: " + dominantPackage);
        assertEquals(1, group.get("dominantPackageMemberCount"));
        assertEquals(0, group.get("nonConformingMemberCount"));
    }

    @Test
    void theMinimumCutIsReportedBesideTheLadder() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> minimumCut = (Map<String, Object>) splitCandidates(crossPackageModule())
                .get("minimumCut");

        assertEquals("OK", minimumCut.get("status"));
        assertEquals(2, minimumCut.get("weight"),
                "cutting either of the two light ends off costs two, which is cheaper than the heavy tie");
    }

    @Test
    void anOversizedModuleStatesWhyTheMinimumCutWasSkipped() throws Exception {
        Map<String, Object> descriptor = splitCandidates(chainModule(401));

        assertEquals(401, descriptor.get("nodeCount"));

        @SuppressWarnings("unchecked")
        Map<String, Object> minimumCut = (Map<String, Object>) descriptor.get("minimumCut");

        assertEquals("NOT_COMPUTED", minimumCut.get("status"));
        assertTrue(((String) minimumCut.get("reason")).contains("400"),
                "the reason has to name the limit, got: " + minimumCut.get("reason"));

        assertFalse(separations(descriptor).isEmpty(),
                "the ladder does not depend on the cubic computation and is still returned");
    }

    @Test
    void theLadderNeedsNoParameterAndRepeats() throws Exception {
        Module module = crossPackageModule();

        assertEquals(splitCandidates(module), splitCandidates(module),
                "the ladder needs no resolution, group count or seed, so two runs agree");
    }

    @Test
    void theToolTakesNothingButAModuleName() throws Exception {
        java.lang.reflect.Method tool = JavaTool.class.getMethod("getSplitCandidates", String.class);

        assertEquals(List.of(String.class), List.of(tool.getParameterTypes()),
                "no desired group count and no seed may be an input of the tool");
    }

    @Test
    void theResultIsBounded() throws Exception {
        Map<String, Object> descriptor = splitCandidates(chainModule(401));

        int limit = (int) descriptor.get("separationLimit");

        assertTrue(separations(descriptor).size() <= limit);

        for (Map<String, Object> separation : separations(descriptor)) {
            assertTrue(groups(separation).size() <= (int) separation.get("listedGroupCount"));
            assertTrue((int) separation.get("groupCount") >= (int) separation.get("listedGroupCount"));
        }
    }

    @Test
    void aModuleWithoutCouplingsHasNoStepToPropose() throws Exception {
        Package demo = new Package("demo");
        demo.addBuildUnit(buildUnit("demo.A"));
        demo.addBuildUnit(buildUnit("demo.B"));

        Module module = new Module("core");
        module.addPackage(demo);

        Map<String, Object> descriptor = splitCandidates(module);

        assertTrue(separations(descriptor).isEmpty(),
                "classes that never reference each other are already separate");
        assertTrue(((String) descriptor.get("reasoning")).contains("already as separate"),
                "the result has to say why there is no proposal, got: " + descriptor.get("reasoning"));
    }
}
