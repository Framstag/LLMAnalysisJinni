package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.java.graph.IntraModuleGraph;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Counts the seams of a project: the interfaces of the project with the classes that implement them, the
 * classes that use them and the modules that import them.
 *
 * <p>The tool counts and does not judge. It knows no framework and no modernisation pattern: whether an
 * interface is a replaceable port is a decision that belongs to the analysis that reads this evidence.
 *
 * <p>All the data comes from the raw Java module reports the engine already writes. The report records for
 * every class whether it is an interface, so that every interface of the project is counted: an interface that
 * declares a default method or a constant and an interface that no class of the project implements alike.
 *
 * <p>The order of the interfaces is a rank: the ones with more implementors and more importing modules come
 * first. No interface is marked good or bad.
 */
public class SeamTool {
    private static final Logger logger = LoggerFactory.getLogger(SeamTool.class);

    private static final String JAVA_REPORT_SUBDIRECTORY = "Java";

    /**
     * The strongest seam comes first: the interfaces with more implementors, then the ones more modules
     * import, then the ones more classes use. The name breaks the ties, so two calls agree.
     */
    private static final Comparator<Seam> SEAM_ORDER = Comparator
            .comparingInt(Seam::implementorCount).reversed()
            .thenComparing(Comparator.comparingInt(Seam::importingModuleCount).reversed())
            .thenComparing(Comparator.comparingInt(Seam::consumerCount).reversed())
            .thenComparing(Seam::interfaceName);

    private final AnalysisContext context;

    public SeamTool(AnalysisContext context) {
        this.context = context;
        logger.info("SeamTool initialized.");
    }

    /**
     * A module of the project with the classes of the module that belong to the module itself and the build
     * units that hold them.
     */
    private record ModuleReport(String moduleName,
                                List<Clazz> classes,
                                List<BuildUnit> buildUnits) {
    }

    @Tool(name = "java_get_seams",
            value = """
                    Counts the seams of the project: for every interface of every Java module, the module that
                    declares it, the classes that implement it with their modules, the classes of the declaring
                    module that reference it without implementing it and the modules of the project that import
                    it.

                    An interface that is implemented more than once and that is imported by more than one
                    module is reported as a port candidate. An interface that no class implements or that no
                    module imports is reported as well, with its empty counts, so that an interface nobody uses
                    can be told apart from a port.

                    The order of the interfaces is a rank: the ones with more implementors and more importing
                    modules come first. No interface is marked good or bad.

                    The counted evidence is written as the CSV report "Seams/Seams.csv" of the workspace, one
                    row per interface. A module whose Java report is missing is named as skipped.

                    The report records for every class whether it is an interface, so that every interface of
                    the project is counted, an interface that declares a default method or a constant and an
                    interface that no class of the project implements alike.
                    """)
    public Map<String, Object> getSeams() throws IOException {
        logger.info("## GetSeams()");

        List<ModuleReport> reports = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

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

            IntraModuleGraph graph = IntraModuleGraph.of(module);

            reports.add(new ModuleReport(moduleName,
                    classesOf(module, graph),
                    buildUnitsOf(module, graph)));
        }

        List<Seam> seams = countSeams(reports);

        SeamReports.write(context.getWorkingDirectory(), seams);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", "Seams");
        result.put("interfaceCount", seams.size());
        result.put("seams", seams.stream().map(SeamTool::descriptor).toList());
        result.put("skipped", skipped);
        result.put("reportFile", SeamReports.REPORT_FILE);

        return result;
    }

    private static List<Seam> countSeams(List<ModuleReport> reports) {
        Map<String, List<Seam.Implementor>> implementorsByInterface = new LinkedHashMap<>();
        Map<String, Set<String>> importingModulesByType = new LinkedHashMap<>();

        for (ModuleReport report : reports) {
            for (Clazz clazz : report.classes()) {
                for (String implemented : clazz.getInterfaces()) {
                    implementorsByInterface.computeIfAbsent(implemented, key -> new ArrayList<>())
                            .add(new Seam.Implementor(clazz.getQualifiedName(), report.moduleName()));
                }
            }

            for (BuildUnit buildUnit : report.buildUnits()) {
                for (String imported : buildUnit.getImports()) {
                    importingModulesByType.computeIfAbsent(imported, key -> new TreeSet<>())
                            .add(report.moduleName());
                }
            }
        }

        List<Seam> seams = new ArrayList<>();

        for (ModuleReport report : reports) {
            for (Clazz clazz : report.classes()) {
                if (!clazz.isInterface()) {
                    continue;
                }

                String interfaceName = clazz.getQualifiedName();
                List<Seam.Implementor> implementors = implementorsOf(implementorsByInterface, interfaceName);
                List<String> consumers = consumersOf(report, interfaceName, implementors);
                List<String> importingModules =
                        new ArrayList<>(importingModulesByType.getOrDefault(interfaceName, Set.of()));

                boolean portCandidate = implementors.size() > 1 && importingModules.size() > 1;

                seams.add(new Seam(interfaceName,
                        report.moduleName(),
                        implementors,
                        consumers,
                        importingModules,
                        portCandidate,
                        reason(implementors.size(), importingModules.size())));
            }
        }

        seams.sort(SEAM_ORDER);

        return seams;
    }

    private static List<Seam.Implementor> implementorsOf(Map<String, List<Seam.Implementor>> implementorsByInterface,
                                                         String interfaceName) {
        List<Seam.Implementor> implementors = new ArrayList<>(
                implementorsByInterface.getOrDefault(interfaceName, List.of()));

        implementors.sort(Comparator.comparing(Seam.Implementor::module)
                .thenComparing(Seam.Implementor::className));

        return implementors;
    }

    /**
     * The classes of the declaring module that reference the interface. The classes that implement the
     * interface are not among them: they provide the interface, they do not consume it.
     *
     * <p>The reference record of the module report is read directly, because the reference graph of the
     * module folds both directions of a tie into one edge and the direction is what tells the referencing
     * class from the referenced one.
     */
    private static List<String> consumersOf(ModuleReport report,
                                            String interfaceName,
                                            List<Seam.Implementor> implementors) {
        String target = ClassFileParser.getBuildUnitName(interfaceName);
        Set<String> referencing = new TreeSet<>();

        for (BuildUnit buildUnit : report.buildUnits()) {
            for (ClassReference reference : buildUnit.getReferences()) {
                if (ClassFileParser.getBuildUnitName(reference.getTarget()).equals(target)) {
                    referencing.add(buildUnit.getName());
                }
            }
        }

        Set<String> implementorNames = new LinkedHashSet<>();

        for (Seam.Implementor implementor : implementors) {
            implementorNames.add(ClassFileParser.getBuildUnitName(implementor.className()));
        }

        referencing.removeAll(implementorNames);

        return new ArrayList<>(referencing);
    }

    private static String reason(int implementorCount, int importingModuleCount) {
        String implementors = switch (implementorCount) {
            case 0 -> "implemented by no class of the project";
            case 1 -> "implemented once";
            default -> "implemented " + implementorCount + " times";
        };

        String importers = switch (importingModuleCount) {
            case 0 -> "imported by no module";
            case 1 -> "imported by 1 module";
            default -> "imported by " + importingModuleCount + " modules";
        };

        return implementors + " and " + importers;
    }

    private static Map<String, Object> descriptor(Seam seam) {
        List<Map<String, Object>> implementors = new ArrayList<>(seam.implementors().size());

        for (Seam.Implementor implementor : seam.implementors()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("className", implementor.className());
            entry.put("module", implementor.module());
            implementors.add(entry);
        }

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("interfaceName", seam.interfaceName());
        descriptor.put("declaringModule", seam.declaringModule());
        descriptor.put("implementors", implementors);
        descriptor.put("implementorCount", seam.implementorCount());
        descriptor.put("consumers", seam.consumers());
        descriptor.put("consumerCount", seam.consumerCount());
        descriptor.put("importingModules", seam.importingModules());
        descriptor.put("importingModuleCount", seam.importingModuleCount());
        descriptor.put("portCandidate", seam.portCandidate());
        descriptor.put("reason", seam.reason());

        return descriptor;
    }

    /**
     * The classes of the module that belong to the module itself. A module report is produced from compiled
     * artefacts, so a module that ships a shaded jar carries the classes of its dependencies as if they were
     * its own. The namespace of the module is what tells them apart.
     */
    private static List<Clazz> classesOf(Module module, IntraModuleGraph graph) {
        List<Clazz> classes = new ArrayList<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!IntraModuleGraph.isInNamespace(buildUnit.getName(), graph.projectNamespace())) {
                    continue;
                }

                for (Clazz clazz : buildUnit.getClazzes()) {
                    if (ParserHelper.isPackageOrModuleInfo(clazz.getQualifiedName())) {
                        continue;
                    }

                    classes.add(clazz);
                }
            }
        }

        return classes;
    }

    private static List<BuildUnit> buildUnitsOf(Module module, IntraModuleGraph graph) {
        List<BuildUnit> buildUnits = new ArrayList<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!IntraModuleGraph.isInNamespace(buildUnit.getName(), graph.projectNamespace())) {
                    continue;
                }

                buildUnits.add(buildUnit);
            }
        }

        return buildUnits;
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

    private static String outdatedReportReason(Module module) {
        String recordedVersion = module.getReportFormatVersion() == 0
                ? "no report format version"
                : "report format version " + module.getReportFormatVersion();

        return "The raw Java module report records " + recordedVersion
                + " instead of version " + Module.CURRENT_REPORT_FORMAT_VERSION
                + " and has to be regenerated before the seams of the module can be counted.";
    }
}
