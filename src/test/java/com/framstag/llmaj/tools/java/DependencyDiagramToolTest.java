package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.java.graph.DependencyDiagrams;
import com.framstag.llmaj.tools.java.graph.IntraModuleGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A diagram is a picture a human reads, and it is derived from the graph alone, so no model output can change
 * it. What matters is that it cannot lie: every omission is in the caption, a group above the budget is
 * reported instead of truncated, and a class name that would break the syntax stops the emission rather than
 * being stored as broken source.
 */
class DependencyDiagramToolTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static final String PROPERTY = "dependencyDiagrams";

    private static BuildUnit buildUnit(String name, ClassReference... references) {
        Clazz clazz = new Clazz(name, null);
        clazz.addMethod(new Method("run", "()V"));

        return new BuildUnit(name, true, false, List.of(), List.of(references), List.of(clazz));
    }

    /**
     * Five classes in three packages. Four of them are tied together heavily and are also related by an
     * inheritance that is never used, and two classes hang off that group by a single light tie each.
     */
    private static Module fixtureModule() {
        Package core = new Package("demo.core");
        core.addBuildUnit(buildUnit("demo.core.Model",
                new ClassReference("demo.api.Dto", 9, 9, false),
                new ClassReference("demo.api.Util", 0, 0, true),
                new ClassReference("demo.core.Repository", 1, 1, false)));
        core.addBuildUnit(buildUnit("demo.core.Service",
                new ClassReference("demo.core.Model", 9, 9, false),
                new ClassReference("demo.api.Util", 9, 9, false)));
        core.addBuildUnit(buildUnit("demo.core.Repository"));

        Package api = new Package("demo.api");
        api.addBuildUnit(buildUnit("demo.api.Dto",
                new ClassReference("demo.core.Service", 9, 9, false)));
        api.addBuildUnit(buildUnit("demo.api.Util",
                new ClassReference("demo.other.Tiny", 1, 1, false)));

        Package other = new Package("demo.other");
        other.addBuildUnit(buildUnit("demo.other.Tiny"));

        Module module = new Module("core");
        module.addPackage(core);
        module.addPackage(api);
        module.addPackage(other);

        return module;
    }

    private record Workspace(JavaTool javaTool, ObjectNode state) {
    }

    private Workspace workspace(Module module) throws Exception {
        Files.createDirectories(tempDir.resolve("Java"));
        objectMapper.writeValue(tempDir.resolve("Java/Java_core.json").toFile(), module);

        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");
        ObjectNode moduleNode = modules.addObject();
        moduleNode.put("name", "core");
        moduleNode.put("path", ".");

        ArrayNode languages = moduleNode.putObject("programmingLanguages").putArray("programmingLanguages");
        languages.addObject().put("name", "Java").put("version", "unknown");

        return new Workspace(new JavaTool(new AnalysisContext(tempDir, tempDir, Map.of(), state)), state);
    }

    private static ObjectNode storedModule(Workspace workspace, String moduleName) {
        return (ObjectNode) workspace.state().get(PROPERTY).get(moduleName);
    }

    private static String overviewSource(Workspace workspace) {
        return storedModule(workspace, "core").get("overview").get("source").asText();
    }

    private static String groupSource(Workspace workspace, int index) {
        return storedModule(workspace, "core").get("groupDiagrams").get(index).get("source").asText();
    }

    private static String groupCaption(Workspace workspace, int index) {
        return storedModule(workspace, "core").get("groupDiagrams").get(index).get("caption").asText();
    }

    @Test
    void theDiagramsAreWrittenIntoTheAnalysisState() throws Exception {
        Workspace workspace = workspace(fixtureModule());

        Map<String, Object> result = workspace.javaTool().generateDependencyDiagrams("core");

        assertEquals("OK", result.get("status"));
        assertNotNull(workspace.state().get(PROPERTY), "the engine persists the analysis state, so this is "
                + "where the documentation finds the source");
        assertNotNull(storedModule(workspace, "core").get("overview"));
        assertFalse(storedModule(workspace, "core").get("groupDiagrams").isEmpty());
        assertTrue(overviewSource(workspace).startsWith("@startuml"));
        assertTrue(overviewSource(workspace).strip().endsWith("@enduml"));
    }

    @Test
    void aRepeatedCallReplacesTheEntryInsteadOfAppending() throws Exception {
        Workspace workspace = workspace(fixtureModule());

        workspace.javaTool().generateDependencyDiagrams("core");
        int firstCount = storedModule(workspace, "core").get("groupDiagrams").size();
        String firstOverview = overviewSource(workspace);

        workspace.javaTool().generateDependencyDiagrams("core");

        assertEquals(1, workspace.state().get(PROPERTY).size(),
                "a module has one entry, a second call must not add a second diagram set");
        assertEquals(firstCount, storedModule(workspace, "core").get("groupDiagrams").size());
        assertEquals(firstOverview, overviewSource(workspace));
    }

    @Test
    void thePackageOverviewAggregatesTheClassLevelWeights() throws Exception {
        Workspace workspace = workspace(fixtureModule());

        workspace.javaTool().generateDependencyDiagrams("core");

        String overview = overviewSource(workspace);

        assertTrue(overview.contains("package \"demo.core\""), "got: " + overview);
        assertTrue(overview.contains("package \"demo.api\""), "got: " + overview);
        assertTrue(overview.contains(" : 27"),
                "nine between Model and Dto, nine between Service and Util and nine between Dto and Service"
                        + " is twenty seven, got: " + overview);
    }

    @Test
    void theClassDetailDrawsTheSeparationAndTheCollapsedOtherGroups() throws Exception {
        Workspace workspace = workspace(fixtureModule());

        workspace.javaTool().generateDependencyDiagrams("core");

        String largestGroup = groupSource(workspace, 0);

        assertTrue(largestGroup.contains("class \"demo.core.Model\""), "got: " + largestGroup);
        assertTrue(largestGroup.contains("rectangle \"group"),
                "the other groups of the separation have to be visible, not merely asserted");
        assertTrue(largestGroup.contains(" : 1"),
                "the coupling to a collapsed group is drawn with its weight, got: " + largestGroup);
    }

    @Test
    void aStructuralRelationIsDrawnEvenWithoutAReferenceEdge() throws Exception {
        Workspace workspace = workspace(fixtureModule());

        workspace.javaTool().generateDependencyDiagrams("core");

        String largestGroup = groupSource(workspace, 0);

        assertTrue(largestGroup.contains("..>"),
                "Model and Util are related by an inheritance that carries no reference site, and it still "
                        + "belongs in the picture, got: " + largestGroup);
    }

    @Test
    void theCaptionStatesTheShownCountAndTheOmittedEdges() throws Exception {
        Workspace workspace = workspace(fixtureModule());

        workspace.javaTool().generateDependencyDiagrams("core");

        String caption = groupCaption(workspace, 0);

        assertTrue(caption.contains("4 of 4 class(es)"),
                "the caption has to state how many classes are shown out of the total, got: " + caption);
        assertTrue(caption.contains("edges below weight 2"),
                "the caption has to name the cutoff, got: " + caption);
        assertTrue(caption.contains("0 omitted"),
                "the caption has to state the omissions even when there are none, got: " + caption);
    }

    @Test
    void aHighCutoffIsReportedAsOmissionsRatherThanHidden() throws Exception {
        Module module = fixtureModule();
        DependencyDiagrams.DiagramSet diagramSet = DependencyDiagrams.of(IntraModuleGraph.of(module),
                new DependencyDiagrams.DiagramSettings(40, 250, 10, 8, 12));

        String caption = diagramSet.groups().getFirst().caption();

        assertTrue(caption.contains("4 omitted"),
                "every internal edge of the group is below the cutoff of ten, got: " + caption);
        assertFalse(diagramSet.groups().getFirst().source().contains(" --> demo"),
                "an omitted edge must not be drawn, got: " + diagramSet.groups().getFirst().source());
    }

    @Test
    void aGroupAboveTheNodeBudgetIsReportedInsteadOfTruncated() throws Exception {
        Module module = fixtureModule();
        DependencyDiagrams.DiagramSet diagramSet = DependencyDiagrams.of(IntraModuleGraph.of(module),
                new DependencyDiagrams.DiagramSettings(3, 250, 2, 8, 12));

        assertTrue(diagramSet.notDrawn().stream().anyMatch(reason -> reason.contains("node budget")),
                "a group above the budget has to be reported, got: " + diagramSet.notDrawn());
        assertTrue(diagramSet.groups().stream().allMatch(diagram -> !diagram.source().contains("demo.core.Model")),
                "the oversized group must not be drawn at all");
    }

    @Test
    void aModuleAboveTheOverviewBudgetStatesWhyThereIsNoOverview() throws Exception {
        Module module = fixtureModule();
        DependencyDiagrams.DiagramSet diagramSet = DependencyDiagrams.of(IntraModuleGraph.of(module),
                new DependencyDiagrams.DiagramSettings(2, 2, 2, 8, 12));

        assertNull(diagramSet.overview(),
                "three packages do not fit into a budget of two nodes");
        assertTrue(diagramSet.notDrawn().stream().anyMatch(reason -> reason.contains("package overview")),
                "the absence has to be explained, got: " + diagramSet.notDrawn());
    }

    @Test
    void aClassNameThatWouldBreakTheSyntaxStopsTheEmission() throws Exception {
        Package demo = new Package("demo");
        demo.addBuildUnit(buildUnit("demo.Bad\"Name"));
        demo.addBuildUnit(buildUnit("demo.Fine",
                new ClassReference("demo.Bad\"Name", 4, 4, false)));

        Module module = new Module("core");
        module.addPackage(demo);

        Workspace workspace = workspace(module);

        workspace.javaTool().generateDependencyDiagrams("core");

        assertTrue(storedModule(workspace, "core").get("overview").isNull(),
                "no diagram may be stored when a class name cannot be represented");
        assertTrue(storedModule(workspace, "core").get("groupDiagrams").isEmpty());
        assertTrue(storedModule(workspace, "core").get("notDrawn").get(0).asText().contains("cannot be written"),
                "the stored entry has to explain the failure, got: "
                        + storedModule(workspace, "core").get("notDrawn"));
    }

    @Test
    void theGeneratedSourceIsValidated() {
        assertTrue(DependencyDiagrams.isValid("@startuml\nclass \"a.B\" as C0\n@enduml\n"));
        assertFalse(DependencyDiagrams.isValid("@startuml\nclass \"a.B\" as C0\n"),
                "source that is never closed is not valid");
        assertFalse(DependencyDiagrams.isValid("@startuml\n@enduml\n"),
                "a diagram without a node says nothing");
        assertFalse(DependencyDiagrams.isValid("@startuml\nclass \"a.B as C0\n@enduml\n"),
                "a label that is never closed is not valid");
        assertFalse(DependencyDiagrams.isValid(null));
    }

    @Test
    void theSameModuleReportAlwaysProducesTheSameSource() throws Exception {
        Workspace first = workspace(fixtureModule());
        JavaTool firstTool = first.javaTool();
        firstTool.generateDependencyDiagrams("core");
        String firstSource = overviewSource(first);

        Workspace second = workspace(fixtureModule());
        second.javaTool().generateDependencyDiagrams("core");

        assertEquals(firstSource, overviewSource(second),
                "no model output reaches the diagram, so two runs cannot differ");
    }

    @Test
    void anOutdatedReportIsReportedInsteadOfAnEmptyDiagram() throws Exception {
        Files.createDirectories(tempDir.resolve("Java"));
        Files.writeString(tempDir.resolve("Java/Java_core.json"), """
                {
                  "name" : "core",
                  "packages" : [ ]
                }
                """);

        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");
        ObjectNode moduleNode = modules.addObject();
        moduleNode.put("name", "core");
        moduleNode.put("path", ".");
        ArrayNode languages = moduleNode.putObject("programmingLanguages").putArray("programmingLanguages");
        languages.addObject().put("name", "Java").put("version", "unknown");

        JavaTool javaTool = new JavaTool(new AnalysisContext(tempDir, tempDir, Map.of(), state));

        Map<String, Object> result = javaTool.generateDependencyDiagrams("core");

        assertEquals("NOT_AVAILABLE", result.get("status"));
        assertNull(state.get(PROPERTY), "nothing may be stored for a report that carries no references");
    }
}
