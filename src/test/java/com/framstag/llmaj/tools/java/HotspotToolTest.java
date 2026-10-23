package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.ToolFactory;
import com.framstag.llmaj.tools.history.FileHistory;
import com.framstag.llmaj.tools.history.HistoryAbsence;
import com.framstag.llmaj.tools.history.HistoryResult;
import com.framstag.llmaj.tools.history.HistoryWindow;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hotspot tool joins the counted history of the project with the counted complexity of the classes. The
 * join is the evidence a hotspot judgement rests on, so both figures have to be exact and the rank has to be
 * a position among the classes of the project, not a threshold that decides the outcome.
 */
class HotspotToolTest {
    private static final String ALIVE_FILE = "src/main/java/demo/core/Alive.java";
    private static final String QUIET_FILE = "src/main/java/demo/core/Quiet.java";
    private static final String FRESH_FILE = "src/main/java/demo/core/Fresh.java";
    private static final String FOREIGN_FILE = "src/main/java/vendor/shaded/Shaded.java";

    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    /**
     * A class of the project, with the file it was declared in and the counted complexity of its methods.
     */
    private static Clazz clazz(String name, String sourceFile, int... methodComplexities) {
        Clazz clazz = new Clazz(name, null);
        clazz.setSuperClass("java.lang.Object");
        clazz.setSourceFile(sourceFile);

        for (int index = 0; index < methodComplexities.length; index++) {
            Method method = new Method("method" + index, "()V");
            method.setCyclomaticComplexity(methodComplexities[index]);
            clazz.addMethod(method);
        }

        return clazz;
    }

    private static BuildUnit buildUnit(String name, Clazz clazz) {
        return new BuildUnit(name, true, false, List.of(), List.of(), List.of(clazz));
    }

    /**
     * The module of the project: a class that changes often and is complicated, a class that changes rarely
     * and is simple, and a class that never changed, however complicated it is.
     */
    private static Module coreModule() {
        Package pck = new Package("demo.core");

        pck.addBuildUnit(buildUnit("demo.core.Alive", clazz("demo.core.Alive", ALIVE_FILE, 40, 20)));
        pck.addBuildUnit(buildUnit("demo.core.Quiet", clazz("demo.core.Quiet", QUIET_FILE, 3)));
        pck.addBuildUnit(buildUnit("demo.core.Fresh", clazz("demo.core.Fresh", FRESH_FILE, 90)));

        Module module = new Module("core");
        module.addPackage(pck);

        return module;
    }

    /**
     * A module whose report holds a build unit outside the namespace of the module: nine classes of the
     * namespace of the module and one class of a shaded dependency, which the module does not own.
     */
    private static Module coreModuleWithForeignBuildUnit() {
        Package pck = new Package("demo.core");

        for (int index = 0; index < 9; index++) {
            pck.addBuildUnit(buildUnit("demo.core.Class" + index,
                    clazz("demo.core.Class" + index, "src/main/java/demo/core/Class" + index + ".java", 5)));
        }

        Package foreign = new Package("vendor.shaded");
        foreign.addBuildUnit(buildUnit("vendor.shaded.Shaded", clazz("vendor.shaded.Shaded", FOREIGN_FILE, 30)));

        Module module = new Module("core");
        module.addPackage(pck);
        module.addPackage(foreign);

        return module;
    }

    private static FileHistory fileHistory(String path, int commitCount, int linesAdded, int linesRemoved) {
        return new FileHistory(path,
                commitCount,
                linesAdded,
                linesRemoved,
                "2026-01-05",
                "2026-10-01",
                List.of("dev"),
                Map.of("dev", commitCount));
    }

    /**
     * The counted history of the project: the file of the often changed class and the file of the rarely
     * changed class are known, the file of the class that never changed is not.
     */
    private static HistoryResult countedHistory() {
        return new HistoryResult(HistoryAbsence.NONE,
                "the history of the repository was counted",
                new HistoryWindow(500, 120, "the most recent 500 commits of the repository",
                        "the commits before the oldest counted commit are left out of the window"),
                50,
                0,
                List.of(fileHistory(ALIVE_FILE, 12, 340, 120),
                        fileHistory(QUIET_FILE, 2, 18, 4)),
                List.of(),
                List.of());
    }

    private static HistoryResult absentHistory(HistoryAbsence absence, String reason) {
        return new HistoryResult(absence,
                reason,
                new HistoryWindow(500, 0, "the most recent 500 commits of the repository",
                        "no commit was counted"),
                50,
                0,
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * The counted history of a copy of the repository that holds only the recent commits: the files of the
     * counted window are known, the classes changed before the window are not.
     */
    private static HistoryResult truncatedHistory() {
        return new HistoryResult(HistoryAbsence.HISTORY_TRUNCATED,
                "the history is truncated, the commits before the oldest commit the copy holds (2026-01-05) are missing",
                new HistoryWindow(500, 120, "the most recent 500 commits of the repository",
                        "the commits before the oldest commit the copy holds (2026-01-05) are missing"),
                50,
                0,
                List.of(fileHistory(ALIVE_FILE, 12, 340, 120),
                        fileHistory(QUIET_FILE, 2, 18, 4)),
                List.of(),
                List.of());
    }

    private void writeReport(String moduleName, Module module) throws IOException {
        Path reportDirectory = tempDir.resolve("Java");
        Files.createDirectories(reportDirectory);
        objectMapper.writeValue(reportDirectory.resolve("Java_" + moduleName + ".json").toFile(), module);
    }

    private AnalysisContext contextOf(String... moduleNames) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : moduleNames) {
            ObjectNode module = modules.addObject();
            module.put("name", moduleName);
            module.put("path", ".");

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            languages.addObject().put("name", "Java").put("version", "unknown");
        }

        return new AnalysisContext(tempDir, tempDir, Map.of(), state);
    }

    private HotspotTool hotspotTool(HistoryResult history) throws IOException {
        writeReport("core", coreModule());

        return new HotspotTool(contextOf("core"), () -> history);
    }

    /**
     * The tool over a module whose report holds a build unit outside the namespace of the module, and over a
     * history that knows the file of that build unit, so that the build unit would be ranked if it were not
     * outside the namespace.
     */
    private HotspotTool hotspotToolWithForeignBuildUnit(HistoryResult history) throws IOException {
        writeReport("core", coreModuleWithForeignBuildUnit());

        return new HotspotTool(contextOf("core"), () -> history);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> hotspotsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("hotspots");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> excludedBuildUnitsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("excludedBuildUnits");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> moduleClassCountsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("moduleClassCounts");
    }

    private static Map<String, Object> hotspotOf(Map<String, Object> result, String className) {
        return hotspotsOf(result).stream()
                .filter(hotspot -> className.equals(hotspot.get("className")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hotspot for " + className
                        + ", got: " + result.get("hotspots")));
    }

    @Test
    void aClassWithAChangedFileIsReportedWithTheFiguresOfBothSides() throws Exception {
        Map<String, Object> result = hotspotTool(countedHistory()).getHotspots();

        Map<String, Object> alive = hotspotOf(result, "demo.core.Alive");

        assertEquals("core", alive.get("module"));
        assertEquals(ALIVE_FILE, alive.get("sourceFile"));
        assertEquals(12, alive.get("changeCount"));
        assertEquals(340, alive.get("linesAdded"));
        assertEquals(120, alive.get("linesRemoved"));
        assertEquals(60, alive.get("complexity"), "the complexity is the counted complexity of the methods");
        assertEquals(2, alive.get("methodCount"));
    }

    @Test
    void theOftenChangedAndComplicatedClassComesFirstWithBothFiguresBesideItsRank() throws Exception {
        Map<String, Object> result = hotspotTool(countedHistory()).getHotspots();

        List<Map<String, Object>> hotspots = hotspotsOf(result);

        assertEquals("demo.core.Alive", hotspots.getFirst().get("className"));
        assertEquals("demo.core.Quiet", hotspots.get(1).get("className"));

        assertEquals(1, hotspots.getFirst().get("rank"));
        assertEquals(12, hotspots.getFirst().get("changeCount"));
        assertEquals(60, hotspots.getFirst().get("complexity"));

        assertEquals(2, hotspots.get(1).get("rank"));
        assertEquals(2, hotspots.get(1).get("changeCount"));
        assertEquals(3, hotspots.get(1).get("complexity"));
    }

    @Test
    void theRankIsAPositionAmongTheClassesOfTheProjectAndNoThreshold() throws Exception {
        Map<String, Object> result = hotspotTool(countedHistory()).getHotspots();

        Map<String, Object> first = hotspotOf(result, "demo.core.Alive");

        assertTrue(((String) first.get("rankStatement")).contains("ranks first among the classes of this project"),
                first.toString());
        assertTrue(((String) result.get("note")).contains("not a threshold"), result.toString());
    }

    @Test
    void aClassWhoseFileNeverChangedHoldsNoRank() throws Exception {
        Map<String, Object> result = hotspotTool(countedHistory()).getHotspots();

        assertTrue(hotspotsOf(result).stream()
                        .noneMatch(hotspot -> "demo.core.Fresh".equals(hotspot.get("className"))),
                "a class whose file never changed holds no row, because the join needs both figures");
        assertTrue(hotspotsOf(result).stream().noneMatch(hotspot -> Integer.valueOf(0).equals(hotspot.get("rank"))),
                "no class of the ranking holds no rank");
    }

    @Test
    void anAbsentHistoryIsReportedAsAbsentWithItsReasonAndHoldsNoRanking() throws Exception {
        HistoryResult absent = absentHistory(HistoryAbsence.NOT_A_REPOSITORY,
                "the project directory is not a repository");

        Map<String, Object> result = hotspotTool(absent).getHotspots();

        assertEquals(false, result.get("hotspotsAvailable"));
        assertEquals("NOT_A_REPOSITORY", result.get("absence"));
        assertTrue(((String) result.get("reason")).contains("not a repository"), result.toString());
        assertFalse(result.containsKey("hotspots"), "an absent history holds no ranking, not an empty one");
    }

    @Test
    void everyAbsentCaseOfTheHistoryIsReportedAsAbsent() throws Exception {
        List<HistoryAbsence> absences = List.of(HistoryAbsence.NOT_A_REPOSITORY,
                HistoryAbsence.GIT_EXECUTABLE_MISSING);

        for (HistoryAbsence absence : absences) {
            Map<String, Object> result = hotspotTool(absentHistory(absence, "the reason of " + absence))
                    .getHotspots();

            assertEquals(false, result.get("hotspotsAvailable"), absence.name());
            assertEquals(absence.name(), result.get("absence"), absence.name());
            assertTrue(((String) result.get("reason")).contains(absence.name()), result.toString());
            assertFalse(result.containsKey("hotspots"), absence.name());
        }
    }

    @Test
    void aTruncatedHistoryStillHoldsTheHotspotsOfTheCountedWindow() throws Exception {
        Map<String, Object> result = hotspotTool(truncatedHistory()).getHotspots();

        assertEquals(true, result.get("hotspotsAvailable"),
                "a truncated history is a counted history, so the project holds counted hotspots");
        assertEquals(true, result.get("historyTruncated"));

        Map<String, Object> alive = hotspotOf(result, "demo.core.Alive");

        assertEquals(12, alive.get("changeCount"));
        assertEquals(60, alive.get("complexity"));
        assertEquals(1, alive.get("rank"));

        assertTrue(hotspotsOf(result).stream()
                        .noneMatch(hotspot -> "demo.core.Fresh".equals(hotspot.get("className"))),
                "a class the truncated history cannot see is left out of the ranking, it is not a class that never changed");
    }

    @Test
    void aTruncatedHistoryNamesTheTruncationAndTheClassesLeftOut() throws Exception {
        Map<String, Object> result = hotspotTool(truncatedHistory()).getHotspots();

        String statement = (String) result.get("truncationStatement");

        assertNotNull(statement, "the result has to name the truncation");
        assertTrue(statement.contains("truncated"), statement);
        assertTrue(statement.contains("before"), statement);
        assertTrue(statement.contains("left out of the ranking"), statement);
        assertTrue(((String) result.get("truncationReason")).contains("truncated"), result.toString());
        assertEquals("the most recent 500 commits of the repository", result.get("window"));
    }

    @Test
    void theHotspotEvidenceIsWrittenAsACsvReport() throws Exception {
        hotspotTool(countedHistory()).getHotspots();

        List<String> lines = Files.readAllLines(tempDir.resolve(HotspotReports.REPORT_TYPE)
                .resolve("Hotspots.csv"));

        assertEquals("ClassName,Module,ChangeCount,Complexity,Rank", lines.getFirst());
        assertEquals(2, lines.size() - 1, "one row per class of the ranking");

        assertEquals("demo.core.Alive,core,12,60,1", lines.get(1));
        assertEquals("demo.core.Quiet,core,2,3,2", lines.get(2));
    }

    @Test
    void anAbsentHistoryWritesNoCsvReport() throws Exception {
        hotspotTool(absentHistory(HistoryAbsence.GIT_EXECUTABLE_MISSING, "no git executable")).getHotspots();

        assertFalse(Files.exists(tempDir.resolve(HotspotReports.REPORT_TYPE).resolve("Hotspots.csv")),
                "a project without a counted history holds no counted hotspots, so no report is written");
    }

    @Test
    void aTruncatedHistoryWritesTheCsvReportOfTheCountedWindow() throws Exception {
        hotspotTool(truncatedHistory()).getHotspots();

        Path report = tempDir.resolve(HotspotReports.REPORT_TYPE).resolve("Hotspots.csv");

        assertTrue(Files.exists(report), "the hotspots of the counted window are counted, so the report is written");

        List<String> lines = Files.readAllLines(report);

        assertEquals("ClassName,Module,ChangeCount,Complexity,Rank", lines.getFirst());
        assertEquals(2, lines.size() - 1, "one row per class of the ranking");
        assertEquals("demo.core.Alive,core,12,60,1", lines.get(1));
        assertEquals("demo.core.Quiet,core,2,3,2", lines.get(2));
    }

    @Test
    void aBuildUnitOutsideTheNamespaceOfTheModuleIsNamedWithItsReason() throws Exception {
        HistoryResult history = new HistoryResult(HistoryAbsence.NONE,
                "the history of the repository was counted",
                new HistoryWindow(500, 120, "the most recent 500 commits of the repository",
                        "the commits before the oldest counted commit are left out of the window"),
                50,
                0,
                List.of(fileHistory(FOREIGN_FILE, 7, 90, 30)),
                List.of(),
                List.of());

        Map<String, Object> result = hotspotToolWithForeignBuildUnit(history).getHotspots();

        List<Map<String, Object>> excluded = excludedBuildUnitsOf(result);

        assertEquals(1, excluded.size(), "the build unit outside the namespace of the module is named");

        Map<String, Object> excludedBuildUnit = excluded.getFirst();

        assertEquals("vendor.shaded.Shaded", excludedBuildUnit.get("buildUnitName"));
        assertEquals("core", excludedBuildUnit.get("moduleName"));
        assertEquals("outside the namespace of the module", excludedBuildUnit.get("reason"));

        assertTrue(hotspotsOf(result).stream()
                        .noneMatch(hotspot -> "vendor.shaded.Shaded".equals(hotspot.get("className"))),
                "the class of an excluded build unit holds no row");
    }

    @Test
    void theClassCountOfTheModuleHoldsTheClassesOfTheExcludedBuildUnits() throws Exception {
        HistoryResult history = new HistoryResult(HistoryAbsence.NONE,
                "the history of the repository was counted",
                new HistoryWindow(500, 120, "the most recent 500 commits of the repository",
                        "the commits before the oldest counted commit are left out of the window"),
                50,
                0,
                List.of(fileHistory(FOREIGN_FILE, 7, 90, 30)),
                List.of(),
                List.of());

        Map<String, Object> result = hotspotToolWithForeignBuildUnit(history).getHotspots();

        assertEquals(10, result.get("classCount"),
                "the count of the classes of the module holds every class of the module");

        List<Map<String, Object>> moduleClassCounts = moduleClassCountsOf(result);

        assertEquals(1, moduleClassCounts.size());
        assertEquals("core", moduleClassCounts.getFirst().get("moduleName"));
        assertEquals(10, moduleClassCounts.getFirst().get("classCount"),
                "the count of the classes of the module holds the classes of the excluded build units");
    }

    @Test
    void theToolIsRegisteredInTheToolFactory() {
        List<Object> tools = ToolFactory.getToolInstanceList(contextOf("core"));

        assertTrue(tools.stream().anyMatch(tool -> tool instanceof HotspotTool),
                "the hotspot tool has to be part of the tool list of the engine");
    }

    @Test
    void theToolIsNamedInTheJavaFamily() throws Exception {
        java.lang.reflect.Method method = HotspotTool.class.getMethod("getHotspots");

        Tool tool = method.getAnnotation(Tool.class);

        assertNotNull(tool, "the hotspot tool has to be a tool of the engine");
        assertEquals("java_get_hotspots", tool.name());
    }
}
