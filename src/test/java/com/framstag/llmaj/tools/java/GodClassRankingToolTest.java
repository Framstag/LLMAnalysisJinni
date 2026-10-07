package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.java.graph.IntraModuleGraph;
import dev.langchain4j.agent.tool.Tool;
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
 * A god class is a class that is big AND incohesive AND reaches out. Size alone is not the signal: a large
 * class whose methods all work on the state of the class is a well designed large class, and the ranking has
 * to tell the two apart. Nothing here is a threshold, so the interesting assertions are about order and about
 * every factor being visible.
 */
class GodClassRankingToolTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static Method method(String name, int complexity, int internalFieldAccesses,
                                 int foreignFieldAccesses, int lines) {
        Method method = new Method(name, "()V");
        method.setCyclomaticComplexity(complexity);
        method.setInternalFieldAccesses(internalFieldAccesses);
        method.setForeignFieldAccesses(foreignFieldAccesses);
        method.setNestingDepth(1);
        method.setLinesOfCode(lines);

        return method;
    }

    private static List<Method> sixMethods(int internalFieldAccesses, int foreignFieldAccesses, int lines) {
        List<Method> methods = new ArrayList<>();

        for (int index = 0; index < 6; index++) {
            methods.add(method("m" + index, 10, internalFieldAccesses, foreignFieldAccesses, lines));
        }

        return methods;
    }

    private static BuildUnit buildUnit(String name, boolean production, List<Method> methods,
                                       ClassReference... references) {
        Clazz clazz = new Clazz(name, null);
        methods.forEach(clazz::addMethod);

        return new BuildUnit(name, production, false, List.of(), List.of(references), List.of(clazz));
    }

    /**
     * Two large classes of the same size, one whose methods work on its own state and one whose methods do
     * not, beside two small classes so that there is a distribution to rank against.
     */
    private static Module fixtureModule() {
        Package demo = new Package("demo");

        demo.addBuildUnit(buildUnit("demo.CohesiveService", true, sixMethods(1, 0, 5),
                new ClassReference("demo.Small", 5, 5, false)));
        demo.addBuildUnit(buildUnit("demo.IncohesiveService", true, sixMethods(0, 1, 5),
                new ClassReference("demo.Small", 1, 1, false)));
        demo.addBuildUnit(buildUnit("demo.Small", true, List.of(
                method("a", 1, 1, 0, 3),
                method("b", 1, 1, 0, 3))));
        demo.addBuildUnit(buildUnit("demo.Smaller", true, List.of(
                method("a", 1, 1, 0, 3))));
        demo.addBuildUnit(buildUnit("demo.HelperTest", false, sixMethods(1, 0, 5)));

        Module module = new Module("core");
        module.addPackage(demo);

        return module;
    }

    private JavaTool javaTool() throws Exception {
        Files.createDirectories(tempDir.resolve("Java"));
        objectMapper.writeValue(tempDir.resolve("Java/Java_core.json").toFile(), fixtureModule());

        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");
        ObjectNode module = modules.addObject();
        module.put("name", "core");
        module.put("path", ".");

        ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
        languages.addObject().put("name", "Java").put("version", "unknown");

        return new JavaTool(new AnalysisContext(tempDir, tempDir, Map.of(), state));
    }

    private static List<GodClassRanking.RankedClass> ranking(int limit) {
        Module module = fixtureModule();

        return GodClassRanking.of(module, IntraModuleGraph.of(module), limit);
    }

    private static GodClassRanking.RankedClass rankedClass(String name) {
        return ranking(GodClassRanking.DEFAULT_RANKING_LIMIT).stream()
                .filter(entry -> entry.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry for " + name));
    }

    private static GodClassRanking.Factor factorOf(GodClassRanking.RankedClass entry, String factorName) {
        return entry.factors().stream()
                .filter(factor -> factor.name().equals(factorName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no factor " + factorName + " in " + entry));
    }

    @Test
    void anIncohesiveLargeClassOutranksACohesiveLargeClassOfTheSameSize() {
        GodClassRanking.RankedClass incohesive = rankedClass("demo.IncohesiveService");
        GodClassRanking.RankedClass cohesive = rankedClass("demo.CohesiveService");

        assertEquals(factorOf(incohesive, GodClassRanking.FACTOR_SIZE).value(),
                factorOf(cohesive, GodClassRanking.FACTOR_SIZE).value(),
                "both classes are the same size, so size cannot be what tells them apart");

        assertTrue(incohesive.score() > cohesive.score(),
                "the same size with methods that do not work on the state is the worse finding, got "
                        + incohesive.score() + " against " + cohesive.score());
        assertEquals(1, incohesive.rank());
        assertEquals(2, cohesive.rank());
    }

    @Test
    void cohesionIsReportedAsTheLowValueItIs() {
        assertEquals(0.0, factorOf(rankedClass("demo.IncohesiveService"),
                GodClassRanking.FACTOR_COHESION).value());
        assertEquals(1.0, factorOf(rankedClass("demo.CohesiveService"),
                GodClassRanking.FACTOR_COHESION).value());
    }

    @Test
    void smallClassesRankBehindTheLargeOnes() {
        List<String> order = ranking(GodClassRanking.DEFAULT_RANKING_LIMIT).stream()
                .map(GodClassRanking.RankedClass::name)
                .toList();

        assertEquals(List.of("demo.IncohesiveService", "demo.CohesiveService", "demo.Small", "demo.Smaller"),
                order);
    }

    @Test
    void everyFactorIsReportedWithItsValueAndItsPercentile() {
        GodClassRanking.RankedClass entry = rankedClass("demo.IncohesiveService");

        assertEquals(6, entry.factors().size());

        for (GodClassRanking.Factor factor : entry.factors()) {
            assertFalse(factor.name().isEmpty());
            assertTrue(factor.percentile() >= 0 && factor.percentile() <= 100,
                    "a percentile is a share, got " + factor.percentile() + " for " + factor.name());
        }

        assertEquals(4, entry.rankedClassCount(), "four production classes were ranked");
    }

    @Test
    void theDominantFactorIsNamed() {
        assertEquals(GodClassRanking.FACTOR_COHESION,
                rankedClass("demo.IncohesiveService").dominantFactor(),
                "cohesion is what drives the worst class, or the reader cannot check the weighting");
    }

    @Test
    void theEntryLimitBoundsTheResultButNotTheRanking() {
        List<GodClassRanking.RankedClass> limited = ranking(2);

        assertEquals(2, limited.size());
        assertEquals(4, limited.getFirst().rankedClassCount(),
                "the rank is a position among every class of the module, not among the returned ones");
        assertEquals(1, limited.getFirst().rank());
        assertEquals(2, limited.getLast().rank());
    }

    @Test
    void noThresholdAndNoVerdictIsProduced() throws Exception {
        GodClassRanking.RankedClass entry = rankedClass("demo.IncohesiveService");

        assertTrue(entry.factors().stream().allMatch(factor -> factor.percentile() >= 0),
                "a percentile is relative to the module and is not a threshold");

        for (Map.Entry<String, Object> field : descriptorOf("demo.IncohesiveService").entrySet()) {
            assertFalse(field.getValue() instanceof Boolean,
                    "the ranking must not hand out a pass or fail verdict, got a boolean for "
                            + field.getKey());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> descriptorOf(String className) throws Exception {
        Map<String, Object> ranking = (Map<String, Object>) javaTool().getGodClassRanking("core");

        return ((List<Map<String, Object>>) ranking.get("ranking")).stream()
                .filter(entry -> entry.get("name").equals(className))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theToolResultStatesTheCountsAndTheLimit() throws Exception {
        Map<String, Object> descriptor = (Map<String, Object>) javaTool().getGodClassRanking("core");

        assertEquals("OK", descriptor.get("status"));
        assertEquals(4, descriptor.get("classCount"));
        assertEquals(1, descriptor.get("excludedClassCount"),
                "a test class is not ranked, and the omission is stated");
        assertEquals(GodClassRanking.DEFAULT_RANKING_LIMIT, descriptor.get("entryLimit"));
        assertEquals(4, descriptor.get("returnedEntryCount"));
        assertEquals(4, ((List<?>) descriptor.get("ranking")).size());
    }

    @Test
    void cohesionIsDescribedAsAnApproximationInTheToolRegistration() throws Exception {
        Tool tool = JavaTool.class.getMethod("getGodClassRanking", String.class).getAnnotation(Tool.class);
        String description = String.join("\n", tool.value());

        assertTrue(description.contains("TCC-like"),
                "the tool must not claim to compute TCC, got: " + description);
        assertTrue(description.contains("not TCC"),
                "the tool must say what it is not, got: " + description);
    }

    @Test
    void theRankingNeedsNoThresholdToBeRepeatable() {
        assertEquals(ranking(GodClassRanking.DEFAULT_RANKING_LIMIT),
                ranking(GodClassRanking.DEFAULT_RANKING_LIMIT));
    }
}
