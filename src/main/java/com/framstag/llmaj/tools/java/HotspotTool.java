package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.history.FileHistory;
import com.framstag.llmaj.tools.history.HistoryResult;
import com.framstag.llmaj.tools.history.HistoryTool;
import com.framstag.llmaj.tools.java.graph.IntraModuleGraph;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Counts the hotspots of the project: the classes that are both complicated and alive.
 *
 * <p>The tool joins the counted version history of the project with the counted metrics of the raw Java
 * module reports. The change count, the added and the removed lines are the counted history of the file the
 * class was declared in. The complexity and the method count are the counted metrics of the report, taken
 * from it as they stand: the complexity of a class is the sum of the cyclomatic complexity of its methods,
 * which the module report has already counted.
 *
 * <p>The classes are ranked by the two figures together. The two figures mean different things, so they are
 * not multiplied into a single score: every class is placed among the classes of this project by its change
 * count and by its complexity, and the two places are added up. Both figures are reported beside every rank,
 * so a reader who weighs them differently can reorder the list without counting anything again. The rank is
 * a position among the classes of this project, and no threshold decides it.
 *
 * <p>A class whose file never changed holds no rank: the join needs both figures, and a class that was never
 * changed is not evidence of a hotspot, however complicated it is.
 *
 * <p>A project whose counted history cannot be read at all holds no counted hotspots. The result names the
 * absence and its reason instead of reporting an empty ranking, which would read as a project without
 * hotspots.
 *
 * <p>A truncated history is not such a project: the copy of the repository holds the recent commits, so the
 * hotspots of the counted window are counted and reported, and the result names the truncation, so that a
 * reader knows that the classes changed before the window are left out of the ranking.
 *
 * <p>A build unit of a module that lies outside the namespace of the module is left out of the ranking as
 * well, and the result names it with the reason of the exclusion, so that a reader knows which build units
 * the ranking does not hold and why they are not in it. The count of the classes of the modules is reported
 * beside the ranking, so that the reader sees the whole module and not only the part of it that was ranked.
 */
public class HotspotTool {
    private static final Logger logger = LoggerFactory.getLogger(HotspotTool.class);

    private static final String JAVA_REPORT_SUBDIRECTORY = "Java";

    /**
     * The reason a build unit is left out of the ranking: it lies outside the namespace of the module.
     */
    private static final String OUTSIDE_NAMESPACE_REASON = "outside the namespace of the module";

    private final AnalysisContext context;
    private final Supplier<HistoryResult> historySource;

    /**
     * @param context the context of the analysis
     */
    public HotspotTool(AnalysisContext context) {
        this(context, new HistoryTool(context)::readVersionHistory);
    }

    /**
     * @param context       the context of the analysis
     * @param historySource the counted version history the join reads
     */
    public HotspotTool(AnalysisContext context, Supplier<HistoryResult> historySource) {
        this.context = context;
        this.historySource = historySource;
        logger.info("HotspotTool initialized.");
    }

    @Tool(name = "java_get_hotspots",
            value = """
                    Counts the hotspots of the project: for every class of every Java module whose file the
                    counted version history knows, the number of commits that changed the file, the lines added
                    and removed over those commits, the counted cyclomatic complexity of the methods of the
                    class and the number of its methods.

                    The classes are ranked by their change count and their complexity together, and both figures
                    are reported beside every rank, so that a reader who weighs them differently can reorder
                    the list. The rank is a position among the classes of this project, and no threshold
                    decides it.

                    A class whose file never changed holds no rank: the join needs both figures.

                    A project whose counted history cannot be read at all holds no counted hotspots. The result
                    names the absence and its reason instead of reporting an empty ranking.

                    A truncated history is counted: the hotspots of the counted window are reported and the
                    result names the truncation, so that the classes changed before the counted window are
                    left out of the ranking.

                    The counted evidence is written as the CSV report "Hotspots/Hotspots.csv" of the workspace,
                    one row per ranked class. The result holds the count of the classes of the modules beside the
                    ranking. A module whose Java report is missing is named as skipped, and a build unit that
                    lies outside the namespace of its module is named as excluded, with the reason of the
                    exclusion.
                    """)
    public Map<String, Object> getHotspots() throws IOException {
        logger.info("## GetHotspots()");

        HistoryResult history = historySource.get();

        if (!history.isAvailable()) {
            logger.info("# => history absent: {}", history.reason());
            return absentResult(history);
        }

        Map<String, FileHistory> historyByPath = historyByPath(history);

        List<Hotspot> candidates = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        List<Map<String, Object>> excludedBuildUnits = new ArrayList<>();
        List<Map<String, Object>> moduleClassCounts = new ArrayList<>();
        int moduleClassCount = 0;

        for (String moduleName : moduleNames()) {
            Path reportPath = reportPath(moduleName);

            if (!Files.exists(reportPath)) {
                skipped.add(skippedModule(moduleName, "No raw Java module report exists for the module."));
                continue;
            }

            Module module;

            try {
                module = ObjectMapperFactory.getJSONObjectMapperInstance()
                        .readValue(reportPath.toFile(), Module.class);
            } catch (IOException | RuntimeException e) {
                logger.warn("Could not read the Java report of '{}': {}", moduleName, e.getMessage());
                skipped.add(skippedModule(moduleName,
                        "The raw Java module report could not be read: " + e.getMessage()));
                continue;
            }

            if (!module.isReportFormatCurrent()) {
                skipped.add(skippedModule(moduleName, outdatedReportReason(module)));
                continue;
            }

            int classCount = classCountOf(module);

            moduleClassCount += classCount;
            moduleClassCounts.add(moduleClassCount(moduleName, classCount));

            candidates.addAll(candidatesOf(moduleName, module, historyByPath, excludedBuildUnits));
        }

        List<Hotspot> ranked = rank(candidates);

        HotspotReports.write(context.getWorkingDirectory(), ranked);

        logger.info("# => {} hotspots of {} classes", ranked.size(), candidates.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", "Hotspots");
        result.put("hotspotsAvailable", true);
        result.put("classCount", moduleClassCount);
        result.put("moduleClassCounts", moduleClassCounts);
        result.put("hotspotCount", ranked.size());
        result.put("hotspots", descriptorsOf(ranked));
        result.put("window", history.window().description());
        result.put("windowExcludes", history.window().excluded());
        result.put("historyTruncated", history.isTruncated());

        if (history.isTruncated()) {
            result.put("truncationReason", history.reason());
            result.put("truncationStatement", truncationStatement(history));
        }

        result.put("note", "The rank is the position of a class among the classes of this project, by change "
                           + "count and complexity together. It is not a threshold: the same figures in another "
                           + "project can lead to another order.");
        result.put("skipped", skipped);
        result.put("excludedBuildUnits", excludedBuildUnits);
        result.put("excludedBuildUnitCount", excludedBuildUnits.size());
        result.put("reportFile", HotspotReports.REPORT_FILE);

        return result;
    }

    /**
     * The statement of a truncated history. The hotspots of the counted window are counted and reported, and
     * the statement names the truncation and the classes the truncated history cannot see, so that a reader
     * does not read the ranking as the hotspots of the whole history.
     */
    private static String truncationStatement(HistoryResult history) {
        return "The counted version history of the project is truncated: " + history.reason() + ". The hotspots "
               + "are counted for the counted window (" + history.window().description() + ") only, and the "
               + "classes that were changed before that window are left out of the ranking.";
    }

    /**
     * The result of a project whose counted history is absent. The absence is named with its reason, and no
     * ranking is reported: an empty ranking would read as a project without hotspots.
     */
    private static Map<String, Object> absentResult(HistoryResult history) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", "Hotspots");
        result.put("hotspotsAvailable", false);
        result.put("absence", history.absence().name());
        result.put("reason", history.reason());
        result.put("note", "The counted history of the project is absent, so the project holds no counted "
                           + "hotspots. The absence is not a project without hotspots.");

        return result;
    }

    /**
     * The counted history per file, keyed by the path of the file. A file the history does not know holds no
     * entry, so the join leaves the classes of that file out.
     */
    private static Map<String, FileHistory> historyByPath(HistoryResult history) {
        Map<String, FileHistory> byPath = new HashMap<>();

        for (FileHistory file : history.files()) {
            byPath.put(file.path(), file);
        }

        return byPath;
    }

    /**
     * The classes of the module that the counted history knows. A class whose file never changed is left out:
     * the join needs both figures.
     *
     * <p>A build unit that lies outside the namespace of the module is left out of the ranking as well, and it
     * is named in the excluded build units, so that a reader knows which build units the ranking does not
     * hold and why they are not in it.
     */
    private static List<Hotspot> candidatesOf(String moduleName,
                                              Module module,
                                              Map<String, FileHistory> historyByPath,
                                              List<Map<String, Object>> excludedBuildUnits) {
        IntraModuleGraph graph = IntraModuleGraph.of(module);

        List<Hotspot> candidates = new ArrayList<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!IntraModuleGraph.isInNamespace(buildUnit.getName(), graph.projectNamespace())) {
                    excludedBuildUnits.add(excludedBuildUnit(moduleName,
                            buildUnit.getName(),
                            OUTSIDE_NAMESPACE_REASON));
                    continue;
                }

                for (Clazz clazz : buildUnit.getClazzes()) {
                    if (ParserHelper.isPackageOrModuleInfo(clazz.getQualifiedName())) {
                        continue;
                    }

                    String sourceFile = clazz.getSourceFile();

                    if (sourceFile == null || sourceFile.isBlank()) {
                        continue;
                    }

                    FileHistory file = historyByPath.get(sourceFile);

                    if (file == null) {
                        continue;
                    }

                    candidates.add(new Hotspot(clazz.getQualifiedName(),
                            moduleName,
                            sourceFile,
                            file.commitCount(),
                            file.linesAdded(),
                            file.linesRemoved(),
                            complexityOf(clazz),
                            clazz.getMethods().size(),
                            0));
                }
            }
        }

        return candidates;
    }

    /**
     * The count of the classes of the module. The count holds every class of the module, also the classes of a
     * build unit that lies outside the namespace of the module, so that the reader can tell how many classes
     * the module holds beside the ones the ranking was counted from.
     */
    private static int classCountOf(Module module) {
        int classCount = 0;

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                classCount += buildUnit.getClazzes().size();
            }
        }

        return classCount;
    }

    /**
     * The counted complexity of the class: the sum of the cyclomatic complexity of its methods, as the raw
     * module report counted it. The complexity is not counted again here.
     */
    private static int complexityOf(Clazz clazz) {
        int complexity = 0;

        for (Method method : clazz.getMethods()) {
            if (method.getCyclomaticComplexity() != null) {
                complexity += method.getCyclomaticComplexity();
            }
        }

        return complexity;
    }

    /**
     * The order of the classes by the two figures together.
     *
     * <p>Every class is placed among the classes of this project by its change count and by its complexity,
     * and the two places are added up. The first place is the class with the most changes, the second one the
     * class with the highest complexity. A class that is high in one figure and low in the other is held back
     * by the figure it is low in, and a class that is high in both comes first.
     *
     * <p>Both figures are reported beside every rank, so a reader who weighs them differently can reorder the
     * list without counting anything again.
     */
    private static List<Hotspot> rank(List<Hotspot> candidates) {
        int size = candidates.size();
        int[] changePlaces = new int[size];
        int[] complexityPlaces = new int[size];

        for (int i = 0; i < size; i++) {
            changePlaces[i] = 1;
            complexityPlaces[i] = 1;

            for (int j = 0; j < size; j++) {
                if (candidates.get(j).changeCount() > candidates.get(i).changeCount()) {
                    changePlaces[i]++;
                }
                if (candidates.get(j).complexity() > candidates.get(i).complexity()) {
                    complexityPlaces[i]++;
                }
            }
        }

        List<Integer> order = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            order.add(i);
        }

        order.sort(Comparator.comparingInt((Integer index) -> changePlaces[index] + complexityPlaces[index])
                .thenComparing(Comparator.comparingInt((Integer index) ->
                        candidates.get(index).changeCount()).reversed())
                .thenComparing(Comparator.comparingInt((Integer index) ->
                        candidates.get(index).complexity()).reversed())
                .thenComparing(index -> candidates.get(index).className()));

        List<Hotspot> ranked = new ArrayList<>(size);

        for (int position = 0; position < order.size(); position++) {
            Hotspot hotspot = candidates.get(order.get(position));

            ranked.add(new Hotspot(hotspot.className(),
                    hotspot.module(),
                    hotspot.sourceFile(),
                    hotspot.changeCount(),
                    hotspot.linesAdded(),
                    hotspot.linesRemoved(),
                    hotspot.complexity(),
                    hotspot.methodCount(),
                    position + 1));
        }

        return ranked;
    }

    /**
     * The descriptors of the ranked classes. Every rank states the position of the class among the classes of
     * this project, so that a reader knows what the rank means without knowing the project.
     */
    private static List<Map<String, Object>> descriptorsOf(List<Hotspot> ranked) {
        List<Map<String, Object>> descriptors = new ArrayList<>(ranked.size());

        for (int position = 0; position < ranked.size(); position++) {
            descriptors.add(descriptor(ranked.get(position), position + 1, ranked.size()));
        }

        return descriptors;
    }

    private static Map<String, Object> descriptor(Hotspot hotspot, int rank, int total) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("className", hotspot.className());
        descriptor.put("module", hotspot.module());
        descriptor.put("sourceFile", hotspot.sourceFile());
        descriptor.put("changeCount", hotspot.changeCount());
        descriptor.put("linesAdded", hotspot.linesAdded());
        descriptor.put("linesRemoved", hotspot.linesRemoved());
        descriptor.put("complexity", hotspot.complexity());
        descriptor.put("methodCount", hotspot.methodCount());
        descriptor.put("rank", hotspot.rank());
        descriptor.put("rankStatement", rankStatement(rank, total));

        return descriptor;
    }

    /**
     * The statement of a rank. The rank is a position among the classes of this project, so the statement names
     * the position and the number of classes it is a position among.
     */
    private static String rankStatement(int rank, int total) {
        if (rank == 1) {
            return "This class ranks first among the classes of this project that changed.";
        }

        return "This class ranks " + rank + " of " + total + " among the classes of this project that changed.";
    }

    private List<String> moduleNames() {
        ObjectNode analysisState = context.getAnalysisState();
        JsonNode modulesSection = analysisState.get("modules");

        if (modulesSection == null || modulesSection.isNull()) {
            return List.of();
        }

        JsonNode modulesArray = modulesSection.get("modules");

        if (modulesArray == null || !modulesArray.isArray()) {
            return List.of();
        }

        List<String> moduleNames = new ArrayList<>();

        for (JsonNode module : modulesArray) {
            JsonNode name = module.get("name");

            if (name != null && name.isTextual()) {
                moduleNames.add(name.asText());
            }
        }

        return moduleNames;
    }

    /**
     * The path of the raw Java module report, named the way the report collection names it.
     */
    private Path reportPath(String moduleName) {
        String reportId = "Java_" + moduleName.replace(" ", "_");

        return context.getWorkingDirectory().resolve(JAVA_REPORT_SUBDIRECTORY).resolve(reportId + ".json");
    }

    private static Map<String, Object> skippedModule(String moduleName, String reason) {
        Map<String, Object> skipped = new LinkedHashMap<>();
        skipped.put("moduleName", moduleName);
        skipped.put("reason", reason);

        return skipped;
    }

    /**
     * The count of the classes of a module, named with the module it belongs to.
     */
    private static Map<String, Object> moduleClassCount(String moduleName, int classCount) {
        Map<String, Object> count = new LinkedHashMap<>();
        count.put("moduleName", moduleName);
        count.put("classCount", classCount);

        return count;
    }

    /**
     * A build unit that is left out of the ranking, named with the module it belongs to and the reason of the
     * exclusion.
     */
    private static Map<String, Object> excludedBuildUnit(String moduleName, String buildUnitName, String reason) {
        Map<String, Object> excluded = new LinkedHashMap<>();
        excluded.put("moduleName", moduleName);
        excluded.put("buildUnitName", buildUnitName);
        excluded.put("reason", reason);

        return excluded;
    }

    private static String outdatedReportReason(Module module) {
        String recordedVersion = module.getReportFormatVersion() == 0
                ? "no report format version"
                : "report format version " + module.getReportFormatVersion();

        return "The raw Java module report records " + recordedVersion
                + " instead of version " + Module.CURRENT_REPORT_FORMAT_VERSION
                + " and has to be regenerated before the hotspots of the module can be counted.";
    }
}
