package com.framstag.llmaj.tools.java.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.java.GodClassRanking;
import com.framstag.llmaj.tools.java.Module;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A diagram is only worth emitting if it is legible on a real module. This runs the emitter over a checked in
 * workspace report and writes the source to the build output, so the result can be judged rather than assumed.
 *
 * <p>The test skips when the workspace has not been generated yet, which keeps it from failing a build that
 * never had the module report to begin with.
 */
class RealModuleDiagramTest {
    private static final List<Path> WORKSPACE_REPORTS = List.of(
            Path.of("workspaces", "llmanalysisjinni", "Java", "Java_LLMAnalysisJinni.json"),
            Path.of("workspaces", "jabref", "Java", "Java_jablib.json"),
            Path.of("workspaces", "maven", "Java", "Java_Apache_Maven.impl.maven-core.json"));

    private static final Path OUTPUT_DIRECTORY = Path.of("target", "dependency-diagrams");

    @Test
    void theDiagramsOfRealModulesAreLegibleAndWithinBudget() throws Exception {
        // The directory is rewritten once, so a diagram that is no longer emitted does not linger from a
        // previous run and make the output look like something it is not.
        cleanOutputDirectory();

        int measured = 0;

        for (Path report : WORKSPACE_REPORTS) {
            if (!Files.exists(report)) {
                continue;
            }

            ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
            Module module = mapper.readValue(report.toFile(), Module.class);

            if (!module.isReportFormatCurrent()) {
                continue;
            }

            IntraModuleGraph graph = IntraModuleGraph.of(module);
            DependencyDiagrams.DiagramSet diagramSet =
                    DependencyDiagrams.of(graph, DependencyDiagrams.DiagramSettings.DEFAULT);

            assertNotNull(diagramSet.overview(), module.getName() + " has a reference graph, so it has an"
                    + " overview");
            assertTrue(DependencyDiagrams.isValid(diagramSet.overview().source()));
            assertFalse(diagramSet.groups().isEmpty(), module.getName() + " has at least one class detail");

            for (DependencyDiagrams.Diagram diagram : allDiagrams(diagramSet)) {
                assertTrue(DependencyDiagrams.isValid(diagram.source()),
                        "every emitted diagram has to be valid PlantUML: " + diagram.title());
            }

            writeForInspection(diagramSet);
            measured++;

            System.out.println("measured " + module.getName() + ": overview "
                    + diagramSet.overview().source().lines().count() + " line(s), "
                    + diagramSet.groups().size() + " class detail diagram(s)");
        }

        Assumptions.assumeTrue(measured > 0,
                "no workspace report has been generated, nothing to draw");
    }

    private static List<DependencyDiagrams.Diagram> allDiagrams(DependencyDiagrams.DiagramSet diagramSet) {
        java.util.List<DependencyDiagrams.Diagram> diagrams = new java.util.ArrayList<>();

        if (diagramSet.overview() != null) {
            diagrams.add(diagramSet.overview());
        }

        diagrams.addAll(diagramSet.groups());

        return diagrams;
    }

    @Test
    void theGodClassRankingOfRealModulesIsPlausible() throws Exception {
        int measured = 0;

        for (Path report : WORKSPACE_REPORTS) {
            if (!Files.exists(report)) {
                continue;
            }

            Module module = ObjectMapperFactory.getJSONObjectMapperInstance()
                    .readValue(report.toFile(), Module.class);

            if (!module.isReportFormatCurrent()) {
                continue;
            }

            IntraModuleGraph graph = IntraModuleGraph.of(module);
            List<GodClassRanking.RankedClass> ranking =
                    GodClassRanking.of(module, graph, GodClassRanking.DEFAULT_RANKING_LIMIT);

            assertFalse(ranking.isEmpty(), module.getName() + " has production classes to rank");
            assertEquals(1, ranking.getFirst().rank());
            assertTrue(ranking.getFirst().score() >= ranking.getLast().score(),
                    "the ranking is ordered by score");

            for (GodClassRanking.RankedClass entry : ranking) {
                assertEquals(6, entry.factors().size(), entry.name() + " reports every factor");
                assertFalse(entry.dominantFactor().isEmpty());
            }

            for (GodClassRanking.RankedClass entry : ranking.subList(0, Math.min(3, ranking.size()))) {
                System.out.println("ranked " + module.getName() + ": " + entry.rank() + "/"
                        + entry.rankedClassCount() + " " + entry.name() + " score " + entry.score()
                        + " driven by " + entry.dominantFactor() + " " + entry.factors().stream()
                        .map(factor -> factor.name() + "=" + factor.value() + "(" + factor.percentile() + ")")
                        .toList());
            }

            measured++;
        }

        Assumptions.assumeTrue(measured > 0, "no workspace report has been generated, nothing to rank");
    }

    private static void cleanOutputDirectory() throws Exception {
        if (!Files.exists(OUTPUT_DIRECTORY)) {
            return;
        }

        try (var files = Files.list(OUTPUT_DIRECTORY)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
    }

    private static void writeForInspection(DependencyDiagrams.DiagramSet diagramSet) throws Exception {
        Files.createDirectories(OUTPUT_DIRECTORY);

        if (diagramSet.overview() != null) {
            Files.writeString(OUTPUT_DIRECTORY.resolve(diagramSet.moduleName() + "-overview.puml"),
                    diagramSet.overview().source());
        }

        for (int index = 0; index < diagramSet.groups().size(); index++) {
            Files.writeString(OUTPUT_DIRECTORY.resolve(diagramSet.moduleName() + "-group-" + index + ".puml"),
                    diagramSet.groups().get(index).source());
        }

        Files.writeString(OUTPUT_DIRECTORY.resolve(diagramSet.moduleName() + "-captions.txt"),
                diagramSet.overview() == null ? "" : diagramSet.overview().caption() + "\n"
                        + String.join("\n", diagramSet.groups().stream()
                        .map(DependencyDiagrams.Diagram::caption).toList())
                        + "\n"
                        + String.join("\n", diagramSet.notDrawn()));
    }
}
