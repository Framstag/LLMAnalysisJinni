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

/**
 * Counts the entry point candidates of a project: the classes a caller can reach a module through.
 *
 * <p>The tool counts and does not judge. It knows no framework and no modernisation pattern: the annotation
 * names and the interface names are reported raw, and it is not said that an annotation marks an entry point
 * or that an interface is a service provider interface. That judgement belongs to the analysis that reads
 * this evidence.
 *
 * <p>All the data comes from the raw Java module reports the engine already writes. A class is a candidate
 * when it declares a main method, when it carries an annotation or when it implements an interface, and the
 * counted evidence of the candidates is written as the CSV report {@code EntryPoints/EntryPoints.csv}.
 *
 * <p>The order of the candidates groups the modules: the candidates are ordered by module and by class name
 * within a module, so that two calls over the same project agree.
 */
public class EntryPointTool {
    private static final Logger logger = LoggerFactory.getLogger(EntryPointTool.class);

    private static final String JAVA_REPORT_SUBDIRECTORY = "Java";

    private static final String PRODUCTION_CODE = "production";
    private static final String TEST_CODE = "test";
    private static final String GENERATED_CODE = "generated";

    private static final String MAIN_METHOD_NAME = "main";

    /**
     * The candidates are grouped by module and ordered by class name within a module, so that two calls over
     * the same project return the same order.
     */
    private static final Comparator<EntryPoint> ENTRY_POINT_ORDER = Comparator
            .comparing(EntryPoint::module)
            .thenComparing(EntryPoint::className);

    private final AnalysisContext context;

    public EntryPointTool(AnalysisContext context) {
        this.context = context;
        logger.info("EntryPointTool initialized.");
    }

    @Tool(name = "java_get_entry_points",
            value = """
                    Counts the entry point candidates of the project: for every class of every Java module, the
                    module the class belongs to, whether the class is production, test or generated code,
                    whether it declares a main method, the names of the annotations the class carries and the
                    interfaces the class implements.

                    A main method is a method named main that is static and returns nothing. A method named
                    main alone, a method named main that is not static and a static method named main that
                    returns a value are no main methods.

                    The tool counts and does not judge: the annotation names and the interface names are
                    reported raw, and it is not said whether an annotation or an interface marks an entry
                    point. That judgement belongs to the analysis that reads this evidence.

                    The candidates are ordered by module and by class name within a module, so that two calls
                    over the same project return the same order.

                    The counted evidence is written as the CSV report "EntryPoints/EntryPoints.csv" of the
                    workspace, one row per class that declares a main method, that carries an annotation or
                    that implements an interface. A module whose Java report is missing is named as skipped.
                    """)
    public Map<String, Object> getEntryPoints() throws IOException {
        logger.info("## GetEntryPoints()");

        List<EntryPoint> candidates = new ArrayList<>();
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

            candidates.addAll(candidatesOf(moduleName, module));
        }

        candidates.sort(ENTRY_POINT_ORDER);

        EntryPointReports.write(context.getWorkingDirectory(), evidenceOf(candidates));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", "EntryPoints");
        result.put("classCount", candidates.size());
        result.put("entryPoints", candidates.stream().map(EntryPointTool::descriptor).toList());
        result.put("skipped", skipped);
        result.put("reportFile", EntryPointReports.REPORT_FILE);

        return result;
    }

    /**
     * The classes of the module that belong to the module itself. A module report is produced from compiled
     * artefacts, so a module that ships a shaded jar carries the classes of its dependencies as if they were
     * its own. The namespace of the module is what tells them apart.
     */
    private static List<EntryPoint> candidatesOf(String moduleName, Module module) {
        IntraModuleGraph graph = IntraModuleGraph.of(module);

        List<EntryPoint> candidates = new ArrayList<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!IntraModuleGraph.isInNamespace(buildUnit.getName(), graph.projectNamespace())) {
                    continue;
                }

                List<Clazz> clazzes = buildUnit.getClazzes();

                if (clazzes == null) {
                    continue;
                }

                String codeCategory = codeCategoryOf(buildUnit);

                for (Clazz clazz : clazzes) {
                    if (ParserHelper.isPackageOrModuleInfo(clazz.getQualifiedName())) {
                        continue;
                    }

                    candidates.add(new EntryPoint(clazz.getQualifiedName(),
                            moduleName,
                            codeCategory,
                            declaresMainMethod(clazz),
                            annotationNamesOf(clazz),
                            interfaceNamesOf(clazz)));
                }
            }
        }

        return candidates;
    }

    /**
     * The classes that can be an entry point: a class that declares a main method, a class that carries an
     * annotation and a class that implements an interface. A class that holds none of the three is counted
     * in the result, but it is no evidence of an entry point and gets no row in the report.
     */
    private static List<EntryPoint> evidenceOf(List<EntryPoint> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate.hasMainMethod()
                        || !candidate.annotations().isEmpty()
                        || !candidate.interfaces().isEmpty())
                .toList();
    }

    /**
     * A main method is a method named main that is static and returns nothing. The return type is read from
     * the descriptor of the method, which holds its parameter list and its return type.
     */
    private static boolean declaresMainMethod(Clazz clazz) {
        for (Method method : clazz.getMethods()) {
            if (MAIN_METHOD_NAME.equals(method.getName()) && method.isStatic() && returnsNothing(method)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return whether the method returns nothing, that is whether its descriptor ends in the void type
     */
    private static boolean returnsNothing(Method method) {
        String descriptor = method.getDescriptor();

        if (descriptor == null) {
            return false;
        }

        int parametersEnd = descriptor.lastIndexOf(')');

        if (parametersEnd < 0 || parametersEnd == descriptor.length() - 1) {
            return false;
        }

        return descriptor.charAt(parametersEnd + 1) == 'V';
    }

    /**
     * The category of the code the class is written in, with the same reading the annotation report of the
     * Java tool uses: generated code is generated, whether it ships as production code or as test code.
     */
    private static String codeCategoryOf(BuildUnit buildUnit) {
        if (buildUnit.isGenerated()) {
            return GENERATED_CODE;
        }

        return buildUnit.isProduction() ? PRODUCTION_CODE : TEST_CODE;
    }

    /**
     * The names of the annotations the class carries. The qualified name is used when the report holds one,
     * the simple name otherwise. The names are reported as they are: the tool knows no framework.
     */
    private static List<String> annotationNamesOf(Clazz clazz) {
        List<String> names = new ArrayList<>();

        for (Annotation annotation : clazz.getAnnotations()) {
            names.add(annotation.getQualifiedName() != null
                    ? annotation.getQualifiedName()
                    : annotation.getName());
        }

        return distinct(names);
    }

    /**
     * The names of the interfaces the class implements. A name that the report holds twice is reported once:
     * a class implements an interface once, however often the report lists it.
     */
    private static List<String> interfaceNamesOf(Clazz clazz) {
        return distinct(clazz.getInterfaces());
    }

    private static List<String> distinct(List<String> names) {
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    private static Map<String, Object> descriptor(EntryPoint candidate) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("className", candidate.className());
        descriptor.put("module", candidate.module());
        descriptor.put("codeCategory", candidate.codeCategory());
        descriptor.put("hasMainMethod", candidate.hasMainMethod());
        descriptor.put("annotations", candidate.annotations());
        descriptor.put("interfaces", candidate.interfaces());

        return descriptor;
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
                + " and has to be regenerated before the entry points of the module can be counted.";
    }
}
