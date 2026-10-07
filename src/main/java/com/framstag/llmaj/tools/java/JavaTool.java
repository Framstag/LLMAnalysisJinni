package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.file.FileHelper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.common.Distribution;
import com.framstag.llmaj.tools.java.graph.DependencyDiagrams;
import com.framstag.llmaj.tools.java.graph.GraphEdge;
import com.framstag.llmaj.tools.java.graph.IntraModuleGraph;
import com.framstag.llmaj.tools.java.graph.MapWeightedGraph;
import com.framstag.llmaj.tools.java.graph.MinimumCut;
import com.framstag.llmaj.tools.java.graph.SeparationLadder;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import com.framstag.llmaj.tools.common.CsvReportWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class JavaTool {
    private static final Logger logger = LoggerFactory.getLogger(JavaTool.class);
    public static final String JAVA_TOOL_JAR_DEPENDENCIES_DIRECTORY_PROPERTY = "javaTool.jarDependenciesDirectory";

    private final AnalysisContext context;
    private final Map<String, Module> moduleMapCache = new HashMap<>();

    private static final String JAVA_REPORT_SUBDIRECTORY = "Java";

    public JavaTool(AnalysisContext context) {
        this.context = context;
        logger.info("JavaTool initialized.");
    }

    private String moduleNameToReportName(String moduleName) {
        return "Java_" + moduleName.replace(" ", "_");
    }

    private static String sanitizeModuleName(String moduleName) {
        return moduleName.replaceAll("[^\\w.-]", "_");
    }

    private JsonNode getModuleNodeByName(ObjectNode analysisState, String moduleName) {
        JsonNode modulesSection = analysisState.get("modules");
        assert (modulesSection != null && !modulesSection.isNull());

        JsonNode modulesArray = modulesSection.get("modules");
        assert (modulesArray != null && !modulesArray.isNull());

        if (modulesArray.isArray()) {
            for (JsonNode module : modulesArray) {
                String currentModuleName = module.get("name").asText();
                if (currentModuleName.equals(moduleName)) {
                    return module;
                }
            }
        }

        return null;
    }

    private Path getModulePath(ObjectNode analysisState, String moduleName) {
        JsonNode module = getModuleNodeByName(analysisState, moduleName);

        assert (module != null && !module.isNull());

        return Path.of(module.get("path").asText());
    }

    private List<SpecialSubdirectory> getSpecialSubdirectoriesFromState(String moduleName, Path modulePath) {
        logger.info("Getting subdirectories from state...");

        List<SpecialSubdirectory> result = new LinkedList<>();

        ObjectNode analysisState = context.getAnalysisState();

        JsonNode module = getModuleNodeByName(analysisState, moduleName);
        assert (module != null && !module.isNull());

        JsonNode subdirectories = module.get("subdirectories");
        assert (subdirectories != null && !subdirectories.isNull());

        JsonNode directories = subdirectories.get("directories");
        assert (directories != null && !directories.isNull());

        for (JsonNode directory : directories) {
            String path = directory.get("path").asText();
            assert (path != null && !path.isEmpty());

            String categoryId = directory.get("categoryId").asText("");
            assert (categoryId != null && !categoryId.isEmpty());

            String description = "";
            JsonNode descriptionNode = directory.get("desc");
            if (descriptionNode != null && descriptionNode.isTextual()) {
                description = descriptionNode.asText();
            }

            result.add(new SpecialSubdirectory(modulePath.resolve(path),
                    SubdirectoryCategory.fromString(categoryId),
                    description));
        }

        logger.info("Getting subdirectories from state... done.");

        return result;
    }

    private static void dumpModuleToLog(Module module) {
        logger.info(" === Packages, BuildUnits, Classes & Methods");

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            logger.info("# {}", pck.getName());

            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                logger.info("  - {}  production={} generated={}", buildUnit.getName(),buildUnit.isProduction(), buildUnit.isGenerated());

                for (String importDesc : buildUnit.getImports().stream().sorted().toList()) {
                    logger.info("    <- {}", importDesc);
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    logger.info("    * {}", clazz.getQualifiedName());

                    for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                        logger.info("    - {} {}", (method.getDescriptor() != null) ? method.getDescriptor() : method.getName(), method.getCyclomaticComplexity());
                    }
                }
            }
        }
    }

    private String storeModuleReport(String moduleName, Module module) throws IOException {
        String reportId = moduleNameToReportName(moduleName);

        logger.info("Id of generated report: {}", reportId);

        Path reportDir = context.getWorkingDirectory().resolve(JAVA_REPORT_SUBDIRECTORY);
        Files.createDirectories(reportDir);
        Path reportPath = reportDir.resolve(reportId + ".json");

        logger.info("Writing report file '{}'...", reportPath);

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
        mapper.writeValue(reportPath.toFile(), module);

        logger.info("Done.");

        moduleMapCache.put(moduleName, module);

        return reportId;
    }

    private Module getModuleReport(String moduleName) throws IOException {
        Module module = moduleMapCache.get(moduleName);

        if (module != null) {
            logger.info("Fetching module report for module '{}' from cache", moduleName);
            return module;
        }

        String reportId = moduleNameToReportName(moduleName);

        Path reportPath = context.getWorkingDirectory().resolve(JAVA_REPORT_SUBDIRECTORY).resolve(reportId + ".json");

        logger.info("Reading report file '{}'...", reportPath);

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
        module = mapper.readValue(reportPath.toFile(), Module.class);

        logger.info("Done.");

        moduleMapCache.put(moduleName, module);

        return module;
    }

    private List<JsonNode> getModuleNodes(ObjectNode analysisState) {
        JsonNode modules = analysisState.get("modules");
        if (modules == null || modules.isNull()) {
            return List.of();
        }

        JsonNode moduleArray = modules.get("modules");
        if (moduleArray == null || !moduleArray.isArray()) {
            return List.of();
        }

        List<JsonNode> result = new ArrayList<>();
        moduleArray.forEach(result::add);
        return result;
    }

    private List<String> getJavaModuleNames(ObjectNode analysisState) {
        return getModuleNodes(analysisState).stream()
                .filter(this::isJavaModule)
                .map(module -> module.get("name").asText())
                .toList();
    }

    private boolean isJavaModule(JsonNode module) {
        JsonNode programmingLanguages = module.get("programmingLanguages");
        if (programmingLanguages == null || programmingLanguages.isNull()) {
            return false;
        }

        JsonNode languageArray = programmingLanguages.get("programmingLanguages");
        if (languageArray == null || !languageArray.isArray()) {
            return false;
        }

        for (JsonNode language : languageArray) {
            JsonNode name = language.get("name");
            if (name != null && "Java".equalsIgnoreCase(name.asText())) {
                return true;
            }
        }

        return false;
    }

    private boolean reportFileExists(String moduleName) {
        return Files.exists(context.getWorkingDirectory().resolve(JAVA_REPORT_SUBDIRECTORY).resolve(moduleNameToReportName(moduleName) + ".json"));
    }

    /**
     * Reads the format version of a report without loading it. A report file can be megabytes large and the
     * version is what decides whether loading it at all is worth anything.
     *
     * @return the recorded version, or empty when the report was written before the version was recorded
     */
    private OptionalInt reportFormatVersion(String moduleName) throws IOException {
        Path reportPath = context.getWorkingDirectory().resolve(JAVA_REPORT_SUBDIRECTORY)
                .resolve(moduleNameToReportName(moduleName) + ".json");

        if (!Files.exists(reportPath)) {
            return OptionalInt.empty();
        }

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

        try (JsonParser parser = mapper.getFactory().createParser(reportPath.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return OptionalInt.empty();
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.currentName();
                parser.nextToken();

                if ("reportFormatVersion".equals(fieldName) && parser.currentToken().isNumeric()) {
                    return OptionalInt.of(parser.getIntValue());
                }

                parser.skipChildren();
            }
        }

        return OptionalInt.empty();
    }

    private Map<String, Object> moduleReportDescriptor(String moduleName,
                                                       String status,
                                                       String reportName,
                                                       String reasoning) {
        return moduleReportDescriptor(moduleName, status, reportName, reasoning, "");
    }

    private Map<String, Object> moduleReportDescriptor(String moduleName,
                                                       String status,
                                                       String reportName,
                                                       String reasoning,
                                                       String programmingLanguage) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", moduleName);
        descriptor.put("status", status);
        if (programmingLanguage != null && !programmingLanguage.isEmpty()) {
            descriptor.put("programmingLanguage", programmingLanguage);
        }
        descriptor.put("reportName", reportName);
        descriptor.put("reasoning", reasoning);
        return descriptor;
    }

    private Map<String, Object> moduleMetricReport(String moduleName,
                                                   String metricName,
                                                   Object report) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("moduleName", moduleName);
        entry.put("metric", metricName);
        entry.put("report", compactMetricReport(report));
        return entry;
    }

    private Object compactMetricReport(Object report) {
        if (report instanceof List<?> list) {
            return list.stream()
                    .map(this::compactMetricItem)
                    .toList();
        }
        return report;
    }

    private Object compactMetricItem(Object item) {
        if (item instanceof Distribution distribution) {
            Map<String, Object> distributionMap = new LinkedHashMap<>();
            distributionMap.put("name", distribution.getName());
            distributionMap.put("entries", distribution.getEntries().stream()
                    .map(entry -> {
                        Map<String, Object> entryMap = new LinkedHashMap<>();
                        entryMap.put("value", entry.getValue());
                        entryMap.put("count", entry.getCount());
                        return entryMap;
                    })
                    .toList());
            return distributionMap;
        }
        return item;
    }

    private <T> Map<String, Object> getAllModuleMetricReports(String metricName,
                                                              MetricReportProvider<T> provider) throws IOException {
        List<Map<String, Object>> reports = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (String moduleName : getJavaModuleNames(context.getAnalysisState())) {
            try {
                T report = provider.get(moduleName);
                reports.add(moduleMetricReport(moduleName, metricName, report));
            } catch (IOException e) {
                skipped.add(moduleReportDescriptor(
                        moduleName,
                        "ERROR",
                        moduleNameToReportName(moduleName),
                        "Could not load or compute Java metric report: " + e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", metricName);
        result.put("reports", reports);
        result.put("skipped", skipped);
        return result;
    }

    @FunctionalInterface
    private interface MetricReportProvider<T> {
        T get(String moduleName) throws IOException;
    }

    /**
     * Builds the type solver for a module, including any configured JAR dependency directory.
     * <p>
     * Package private so the routing of the diagnostics it emits can be tested without a run.
     */
    TypeSolver createTypeResolver(Path rootPath,
                                          Map<String, String> properties,
                                          List<SpecialSubdirectory> specialSubdirectories) throws IOException {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(false)
        );

        if (properties.containsKey(JAVA_TOOL_JAR_DEPENDENCIES_DIRECTORY_PROPERTY)) {
            String jarDependencies = properties.get(JAVA_TOOL_JAR_DEPENDENCIES_DIRECTORY_PROPERTY);
            Path jarDependenciesPath = rootPath.resolve(jarDependencies);

            if (FileHelper.accessAllowed(rootPath, jarDependenciesPath)) {
                Files.walk(jarDependenciesPath, 1) // depth=1 for non-recursive; use Integer.MAX_VALUE for recursive
                        .filter(p -> p.toString().endsWith(".jar"))
                        .forEach(jarPath -> {
                            try {
                                logger.info("Adding '{}' as search path to java parser...", jarPath);
                                try {
                                    typeSolver.add(new JarTypeSolver(jarPath));
                                } catch (NullPointerException e) {
                                    logger.warn("Skip adding '{}' as search path, since there were errors adding it", jarPath, e);
                                }
                            } catch (IOException e) {
                                logger.error("Could not load JAR: {} — {}", jarPath, e.getMessage());
                            }
                        });
            } else {
                logger.error("Not allowed to access '{}' ('{}')", jarDependenciesPath, rootPath);

            }
        }

        for (SpecialSubdirectory directory : specialSubdirectories) {
            if (directory.getCategoryId().isSrc()) {
                Path specialPath = rootPath.resolve(directory.getPath());
                File pathFile = specialPath.toFile();

                if (pathFile.exists() && pathFile.isDirectory()) {
                    logger.info("Adding '{}' as search path to java parser...", specialPath);
                    typeSolver.add(new JavaParserTypeSolver(specialPath.toFile()));
                } else {
                    logger.warn("Skip adding '{}' as search path, since it does no exist or is not a directory", specialPath);
                }
            }
        }

        return typeSolver;
    }

    @Tool(name = "java_generate_module_analysis_report",
            value =
                    """
                            Generates a report file containing raw data for further detailed architecture analysis.
                            
                            Returns the id of the report for further usage.
                            """)
    public String generateModuleAnalysisReport(@P("The name of the module")
                                               String moduleName) throws IOException {
        logger.info("## JavaGenerateModuleAnalysisReport('{}')", moduleName);

        Path rootPath = context.getProjectRoot();

        Path modulePath = rootPath.resolve(getModulePath(context.getAnalysisState(), moduleName));

        logger.info("Module directory: '{}'", modulePath);

        Path startPath = rootPath.resolve(modulePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", startPath, rootPath, modulePath);
            return "";
        }

        List<SpecialSubdirectory> specialSubdirectories = getSpecialSubdirectoriesFromState(moduleName, modulePath);

        specialSubdirectories = filterSubdirectoriesWithAllowedAccess(specialSubdirectories, rootPath);

        ModuleManager moduleManager = new ModuleManager(moduleName);

        int unparsableSrcFiles = 0;
        int unparsableClassFiles = 0;

        List<Path> classFiles = FileHelper.getAllMatchingFilesInDirectoryRecursively(List.of("*.class"),
                startPath);

        logger.info("{} *.class file(s) found", classFiles.size());

        for (Path classFile : classFiles) {
            if (!ClassFileParser.parseClassFile(classFile, specialSubdirectories, moduleManager)) {
                unparsableClassFiles++;
            }
        }

        List<Path> srcFiles = FileHelper.getAllMatchingFilesInDirectoryRecursively(List.of("*.java"), startPath);

        logger.info("{} *.java file(s) found", srcFiles.size());

        TypeSolver typeSolver = createTypeResolver(rootPath, context.getProperties(), specialSubdirectories);

        StaticJavaParser
                .getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));


        for (Path srcFile : srcFiles) {
            if (!JavaFileParser.parseJavaFile(srcFile,
                    specialSubdirectories,
                    moduleManager,
                    typeSolver)) {
                unparsableSrcFiles++;
            }
        }

        // One record per module: a scanned tree can hold thousands of files that carry no type or cannot
        // be parsed, and one record per file would bury every other diagnostic of the run.
        if (unparsableSrcFiles > 0 || unparsableClassFiles > 0) {
            logger.info("Module '{}': {} of {} source file(s) and {} of {} class file(s) were not"
                            + " analysed (the detail per file is at DEBUG)",
                    moduleName, unparsableSrcFiles, srcFiles.size(),
                    unparsableClassFiles, classFiles.size());
        }

        Module module = moduleManager.getModule();

        dumpModuleToLog(module);

        return storeModuleReport(moduleName, module);
    }

    @Tool(name = "java_generate_all_module_analysis_reports",
            value = """
                    Generates or reuses raw Java analysis report files for all detected Java modules.

                    The method reads the current analysis state, selects modules whose programming language is Java,
                    reuses existing Java_<moduleName>.json files by default, and generates missing reports.
                    """)
    public Map<String, Object> generateAllModuleAnalysisReports() throws IOException {
        logger.info("## JavaGenerateAllModuleAnalysisReports()");

        List<Map<String, Object>> reports = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (JsonNode module : getModuleNodes(context.getAnalysisState())) {
            JsonNode nameNode = module.get("name");
            if (nameNode == null || nameNode.isNull()) {
                skipped.add(moduleReportDescriptor("unknown", "ERROR", "", "Module entry has no name."));
                continue;
            }

            String moduleName = nameNode.asText();
            if (!isJavaModule(module)) {
                skipped.add(moduleReportDescriptor(
                        moduleName,
                        "SKIPPED",
                        "",
                        "Module is not detected as Java."));
                continue;
            }

            if (reportFileExists(moduleName)) {
                OptionalInt recordedVersion = reportFormatVersion(moduleName);

                if (recordedVersion.isPresent()
                        && recordedVersion.getAsInt() == Module.CURRENT_REPORT_FORMAT_VERSION) {
                    reports.add(moduleReportDescriptor(
                            moduleName,
                            "REUSED",
                            moduleNameToReportName(moduleName),
                            "Existing Java raw report file reused.",
                            "Java"));
                    continue;
                }

                // Reusing a report that predates the weighted reference record would hand every later tool an
                // unweighted, silently wrong view of the module, so it is regenerated and the reason is named.
                String outdatedReason = recordedVersion.isPresent()
                        ? "Existing Java raw report file records format version " + recordedVersion.getAsInt()
                        + " and was regenerated as version " + Module.CURRENT_REPORT_FORMAT_VERSION + "."
                        : "Existing Java raw report file records no report format version and was regenerated as version "
                        + Module.CURRENT_REPORT_FORMAT_VERSION + ".";

                try {
                    generateModuleAnalysisReport(moduleName);
                    reports.add(moduleReportDescriptor(
                            moduleName,
                            "GENERATED",
                            moduleNameToReportName(moduleName),
                            outdatedReason,
                            "Java"));
                } catch (IOException | RuntimeException e) {
                    reports.add(moduleReportDescriptor(
                            moduleName,
                            "ERROR",
                            moduleNameToReportName(moduleName),
                            outdatedReason + " Regeneration failed: " + e.getMessage(),
                            "Java"));
                }
                continue;
            }

            try {
                generateModuleAnalysisReport(moduleName);
                reports.add(moduleReportDescriptor(
                        moduleName,
                        "GENERATED",
                        moduleNameToReportName(moduleName),
                        "Missing Java raw report generated.",
                        "Java"));
            } catch (IOException | RuntimeException e) {
                reports.add(moduleReportDescriptor(
                        moduleName,
                        "ERROR",
                        moduleNameToReportName(moduleName),
                        "Could not generate Java raw report: " + e.getMessage(),
                        "Java"));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", reports);
        result.put("skipped", skipped);
        return result;
    }

    private static List<SpecialSubdirectory> filterSubdirectoriesWithAllowedAccess(List<SpecialSubdirectory> specialSubdirectories, Path rootPath) {
        List<SpecialSubdirectory> validatedList = new LinkedList<>();

        for (SpecialSubdirectory directory : specialSubdirectories) {
            Path specialPath = directory.getPath();

            if (!FileHelper.accessAllowed(rootPath, specialPath)) {
                logger.error("Not allowed to access '{}' ('{}' '{}')", specialPath, rootPath, directory.getPath());
                continue;
            }

            logger.info("Special directory of type {}: '{}' - {}", directory.getCategoryId(), specialPath, directory.getDescription());

            validatedList.add(directory);
        }
        return validatedList;
    }

    @Tool(name = "java_get_visibility_distribution_report",
            value =
                    """
                            Returns a report regarding the visibility distribution of methods in the module.
                            """)
    public List<Distribution> getVisibilityDistributionReport(@P("The name of the module to analyse")
                                                               String moduleName) throws IOException {
        logger.info("## GetVisibilityDistributionReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Visibility distribution
        Map<String, Integer> prodVisibility = new HashMap<>();
        Map<String, Integer> testVisibility = new HashMap<>();
        Map<String, Integer> genVisibility = new HashMap<>();
        int prodStatic = 0, prodNonStatic = 0;
        int testStatic = 0, testNonStatic = 0;
        int genStatic = 0, genNonStatic = 0;
        int prodFinal = 0, prodNonFinal = 0;
        int testFinal = 0, testNonFinal = 0;
        int genFinal = 0, genNonFinal = 0;

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                Map<String, Integer> targetVis;
                int[] targetStatic;
                int[] targetFinal;

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetVis = prodVisibility;
                    targetStatic = new int[]{prodStatic, prodNonStatic};
                    targetFinal = new int[]{prodFinal, prodNonFinal};
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetVis = testVisibility;
                    targetStatic = new int[]{testStatic, testNonStatic};
                    targetFinal = new int[]{testFinal, testNonFinal};
                } else {
                    targetVis = genVisibility;
                    targetStatic = new int[]{genStatic, genNonStatic};
                    targetFinal = new int[]{genFinal, genNonFinal};
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                        MethodVisibility vis = method.getVisibility();
                        String key = (vis != null) ? vis.name() : "UNKNOWN";
                        targetVis.merge(key, 1, Integer::sum);

                        if (method.isStatic()) {
                            targetStatic[0]++;
                        } else {
                            targetStatic[1]++;
                        }
                        if (method.isFinal()) {
                            targetFinal[0]++;
                        } else {
                            targetFinal[1]++;
                        }
                    }
                }

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    prodStatic = targetStatic[0];
                    prodNonStatic = targetStatic[1];
                    prodFinal = targetFinal[0];
                    prodNonFinal = targetFinal[1];
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    testStatic = targetStatic[0];
                    testNonStatic = targetStatic[1];
                    testFinal = targetFinal[0];
                    testNonFinal = targetFinal[1];
                } else {
                    genStatic = targetStatic[0];
                    genNonStatic = targetStatic[1];
                    genFinal = targetFinal[0];
                    genNonFinal = targetFinal[1];
                }
            }
        }

        Distribution prodVisDist = new Distribution("Visibility distribution production code");
        for (String key : prodVisibility.keySet().stream().sorted().toList()) {
            prodVisDist.addEntry(key, prodVisibility.get(key));
        }
        prodVisDist.addEntry("STATIC", prodStatic);
        prodVisDist.addEntry("FINAL", prodFinal);

        Distribution testVisDist = new Distribution("Visibility distribution test code");
        for (String key : testVisibility.keySet().stream().sorted().toList()) {
            testVisDist.addEntry(key, testVisibility.get(key));
        }
        testVisDist.addEntry("STATIC", testStatic);
        testVisDist.addEntry("FINAL", testFinal);

        Distribution genVisDist = new Distribution("Visibility distribution generated code");
        for (String key : genVisibility.keySet().stream().sorted().toList()) {
            genVisDist.addEntry(key, genVisibility.get(key));
        }
        genVisDist.addEntry("STATIC", genStatic);
        genVisDist.addEntry("FINAL", genFinal);

        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "VisibilityDistribution/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodVisibility, testVisibility, genVisibility);
        return List.of(prodVisDist, testVisDist, genVisDist);
    }
    @Tool(name = "java_get_inheritance_report",
            value =
                    """
                            Returns a report regarding the inheritance metrics in the module.
                            """)
    public List<Distribution> getInheritanceReport(@P("The name of the module to analyse")
                                                    String moduleName) throws IOException {
        logger.info("## GetInheritanceReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Inheritance depth per class: count classes by depth
        Map<Integer, Integer> prodDepth = new HashMap<>();
        Map<Integer, Integer> testDepth = new HashMap<>();
        Map<Integer, Integer> genDepth = new HashMap<>();
        // Interface count per class
        Map<Integer, Integer> prodIfaceCount = new HashMap<>();
        Map<Integer, Integer> testIfaceCount = new HashMap<>();
        Map<Integer, Integer> genIfaceCount = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                Map<Integer, Integer> targetDepth;
                Map<Integer, Integer> targetIface;

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetDepth = prodDepth;
                    targetIface = prodIfaceCount;
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetDepth = testDepth;
                    targetIface = testIfaceCount;
                } else {
                    targetDepth = genDepth;
                    targetIface = genIfaceCount;
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    // Compute inheritance depth by traversing superClass chain
                    int depth = computeInheritanceDepth(buildUnit, clazz, module, new HashSet<>());
                    targetDepth.merge(depth, 1, Integer::sum);

                    // Count interfaces
                    int ifaceCount = clazz.getInterfaces() != null ? clazz.getInterfaces().size() : 0;
                    targetIface.merge(ifaceCount, 1, Integer::sum);
                }
            }
        }

        Distribution prodDepthDist = new Distribution("Inheritance depth production code");
        for (int d : prodDepth.keySet().stream().sorted().toList()) {
            prodDepthDist.addEntry(Integer.toString(d), prodDepth.get(d));
        }

        Distribution testDepthDist = new Distribution("Inheritance depth test code");
        for (int d : testDepth.keySet().stream().sorted().toList()) {
            testDepthDist.addEntry(Integer.toString(d), testDepth.get(d));
        }

        Distribution genDepthDist = new Distribution("Inheritance depth generated code");
        for (int d : genDepth.keySet().stream().sorted().toList()) {
            genDepthDist.addEntry(Integer.toString(d), genDepth.get(d));
        }

        Distribution prodIfaceDist = new Distribution("Interface count production code");
        for (int c : prodIfaceCount.keySet().stream().sorted().toList()) {
            prodIfaceDist.addEntry(Integer.toString(c), prodIfaceCount.get(c));
        }

        Distribution testIfaceDist = new Distribution("Interface count test code");
        for (int c : testIfaceCount.keySet().stream().sorted().toList()) {
            testIfaceDist.addEntry(Integer.toString(c), testIfaceCount.get(c));
        }

        Distribution genIfaceDist = new Distribution("Interface count generated code");
        for (int c : genIfaceCount.keySet().stream().sorted().toList()) {
            genIfaceDist.addEntry(Integer.toString(c), genIfaceCount.get(c));
        }

        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "InheritanceDepth/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodDepth, testDepth, genDepth);
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "InterfaceCount/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodIfaceCount, testIfaceCount, genIfaceCount);
return List.of(prodDepthDist, testDepthDist, genDepthDist,
                prodIfaceDist, testIfaceDist, genIfaceDist);
    }

    private int computeInheritanceDepth(BuildUnit buildUnit, Clazz clazz, Module module, Set<String> visited) {
        if (clazz.getSuperClass() == null || clazz.getSuperClass().isEmpty()) {
            return 0;
        }

        // Prevent cycles
        if (visited.contains(clazz.getSuperClass())) {
            return 0;
        }
        visited.add(clazz.getSuperClass());

        // Search all classes in the module for the superclass
        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                for (Clazz c : bu.getClazzes()) {
                    if (clazz.getSuperClass().equals(c.getQualifiedName())) {
                        return 1 + computeInheritanceDepth(bu, c, module, visited);
                    }
                }
            }
        }

        // Superclass not in this module, treat as depth 1
        return 1;
    }

    @Tool(name = "java_get_method_complexity_report",
            value =
                    """
                            Returns a report regarding the method complexity (parameter count, lines of code) in the module.
                            """)
    public List<Distribution> getMethodComplexityReport(@P("The name of the module to analyse")
                                                         String moduleName) throws IOException {
        logger.info("## GetMethodComplexityReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Parameter count per method
        Map<Integer, Integer> prodParamCount = new HashMap<>();
        Map<Integer, Integer> testParamCount = new HashMap<>();
        Map<Integer, Integer> genParamCount = new HashMap<>();
        // Lines of code per method
        Map<Integer, Integer> prodLoc = new HashMap<>();
        Map<Integer, Integer> testLoc = new HashMap<>();
        Map<Integer, Integer> genLoc = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                Map<Integer, Integer> targetParams;
                Map<Integer, Integer> targetLoc;

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetParams = prodParamCount;
                    targetLoc = prodLoc;
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetParams = testParamCount;
                    targetLoc = testLoc;
                } else {
                    targetParams = genParamCount;
                    targetLoc = genLoc;
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                        // Parameter count
                        targetParams.merge(method.getParameterCount(), 1, Integer::sum);

                        // Lines of code (only if available from source parsing)
                        if (method.getLinesOfCode() != null) {
                            targetLoc.merge(method.getLinesOfCode(), 1, Integer::sum);
                        }
                    }
                }
            }
        }

        Distribution prodParamDist = new Distribution("Parameter count production code");
        for (int c : prodParamCount.keySet().stream().sorted().toList()) {
            prodParamDist.addEntry(Integer.toString(c), prodParamCount.get(c));
        }

        Distribution testParamDist = new Distribution("Parameter count test code");
        for (int c : testParamCount.keySet().stream().sorted().toList()) {
            testParamDist.addEntry(Integer.toString(c), testParamCount.get(c));
        }

        Distribution genParamDist = new Distribution("Parameter count generated code");
        for (int c : genParamCount.keySet().stream().sorted().toList()) {
            genParamDist.addEntry(Integer.toString(c), genParamCount.get(c));
        }

        Distribution prodLocDist = new Distribution("Lines of code production code");
        for (int c : prodLoc.keySet().stream().sorted().toList()) {
            prodLocDist.addEntry(Integer.toString(c), prodLoc.get(c));
        }

        Distribution testLocDist = new Distribution("Lines of code test code");
        for (int c : testLoc.keySet().stream().sorted().toList()) {
            testLocDist.addEntry(Integer.toString(c), testLoc.get(c));
        }

        Distribution genLocDist = new Distribution("Lines of code generated code");
        for (int c : genLoc.keySet().stream().sorted().toList()) {
            genLocDist.addEntry(Integer.toString(c), genLoc.get(c));
        }

        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "MethodParamCount/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodParamCount, testParamCount, genParamCount);
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "MethodLinesOfCode/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodLoc, testLoc, genLoc);
return List.of(prodParamDist, testParamDist, genParamDist,
                prodLocDist, testLocDist, genLocDist);
    }

    @Tool(name = "java_get_method_nesting_depth_report",
            value =
                    """
                            Returns a report regarding the method nesting depth in the module.
                            """)
    public List<Distribution> getMethodNestingDepthReport(@P("The name of the module to analyse")
                                                           String moduleName) throws IOException {
        logger.info("## GetMethodNestingDepthReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Nesting depth per method
        Map<Integer, Integer> prodDepth = new HashMap<>();
        Map<Integer, Integer> testDepth = new HashMap<>();
        Map<Integer, Integer> genDepth = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                Map<Integer, Integer> targetDepth;

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetDepth = prodDepth;
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetDepth = testDepth;
                } else {
                    targetDepth = genDepth;
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                        targetDepth.merge(method.getNestingDepth(), 1, Integer::sum);
                    }
                }
            }
        }

        Distribution prodDepthDist = new Distribution("Nesting depth production code");
        for (int d : prodDepth.keySet().stream().sorted().toList()) {
            prodDepthDist.addEntry(Integer.toString(d), prodDepth.get(d));
        }

        Distribution testDepthDist = new Distribution("Nesting depth test code");
        for (int d : testDepth.keySet().stream().sorted().toList()) {
            testDepthDist.addEntry(Integer.toString(d), testDepth.get(d));
        }

        Distribution genDepthDist = new Distribution("Nesting depth generated code");
        for (int d : genDepth.keySet().stream().sorted().toList()) {
            genDepthDist.addEntry(Integer.toString(d), genDepth.get(d));
        }

        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "NestingDepth/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodDepth, testDepth, genDepth);
        return List.of(prodDepthDist, testDepthDist, genDepthDist);
    }





    @Tool(name = "java_get_cyclomatic_complexity_module_report",
            value =
                    """
                            Returns a report regarding the cyclomatic complexity of the module.
                            """)
    public List<Distribution> getCyclomaticComplexityModuleReport(@P("The name of the module to analyse")
                                                                  String moduleName) throws IOException {
        logger.info("## GetCyclomaticComplexityModuleReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        Map<Integer, Integer> prodDistribution = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                        for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                            if (method.getCyclomaticComplexity() != null) {
                                Integer currentCount = prodDistribution.getOrDefault(method.getCyclomaticComplexity(), 0);
                                prodDistribution.put(method.getCyclomaticComplexity(), currentCount + 1);

                            }
                        }
                    }
                }
            }
        }

        Distribution prodCC = new Distribution("Distribution CyclomaticComplexity production code");

        for (Integer cc : prodDistribution.keySet().stream().sorted().toList()) {
            prodCC.addEntry(cc.toString(), prodDistribution.get(cc));
        }

        Map<Integer, Integer> testDistribution = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                        for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                            if (method.getCyclomaticComplexity() != null) {
                                Integer currentCount = testDistribution.getOrDefault(method.getCyclomaticComplexity(), 0);
                                testDistribution.put(method.getCyclomaticComplexity(), currentCount + 1);

                            }
                        }
                    }
                }
            }
        }

        Distribution testCC = new Distribution("Distribution CyclomaticComplexity test code");

        for (Integer cc : testDistribution.keySet().stream().sorted().toList()) {
            testCC.addEntry(cc.toString(), testDistribution.get(cc));
        }


        Map<Integer, Integer> genDistribution = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                if (buildUnit.isGenerated()) {
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                        for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                            if (method.getCyclomaticComplexity() != null) {
                                Integer currentCount = genDistribution.getOrDefault(method.getCyclomaticComplexity(), 0);
                                genDistribution.put(method.getCyclomaticComplexity(), currentCount + 1);

                            }
                        }
                    }
                }
            }
        }

        Distribution genCC = new Distribution("Distribution CyclomaticComplexity generated code");

        for (Integer cc : genDistribution.keySet().stream().sorted().toList()) {
            genCC.addEntry(cc.toString(), genDistribution.get(cc));
        }

        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "CyclomaticComplexity/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodDistribution, testDistribution, genDistribution);
return List.of(prodCC, testCC, genCC);
    }

    @Tool(name = "java_get_field_visibility_report",
            value =
                    """
                            Returns a report regarding the field visibility distribution in the module.
                            """)
    public List<Distribution> getFieldVisibilityReport(@P("The name of the module to analyse")
                                                        String moduleName) throws IOException {
        logger.info("## GetFieldVisibilityReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        Map<String, Integer> prodFields = new HashMap<>();
        Map<String, Integer> testFields = new HashMap<>();
        Map<String, Integer> genFields = new HashMap<>();
        int prodStatic = 0, testStatic = 0, genStatic = 0;
        int prodFinal = 0, testFinal = 0, genFinal = 0;
        int prodTotal = 0, testTotal = 0, genTotal = 0;

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                Map<String, Integer> targetMap;
                int[] targetStatic;
                int[] targetFinal;
                int[] targetTotal;

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetMap = prodFields; targetStatic = new int[]{prodStatic, 0};
                    targetFinal = new int[]{prodFinal, 0}; targetTotal = new int[]{prodTotal};
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetMap = testFields; targetStatic = new int[]{testStatic, 0};
                    targetFinal = new int[]{testFinal, 0}; targetTotal = new int[]{testTotal};
                } else {
                    targetMap = genFields; targetStatic = new int[]{genStatic, 0};
                    targetFinal = new int[]{genFinal, 0}; targetTotal = new int[]{genTotal};
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    for (Field field : clazz.getFields()) {
                        String key = field.getVisibility() != null ? field.getVisibility().name() : "UNKNOWN";
                        targetMap.merge(key, 1, Integer::sum);
                        if (field.isStatic()) targetStatic[0]++; else targetStatic[1]++;
                        if (field.isFinal()) targetFinal[0]++; else targetFinal[1]++;
                        targetTotal[0]++;
                    }
                }
            }
        }

        Distribution prodDist = new Distribution("Field visibility production code");
        prodDist.addEntry("STATIC", prodStatic);
        prodDist.addEntry("FINAL", prodFinal);
        prodDist.addEntry("TOTAL", prodTotal);
        for (String k : prodFields.keySet().stream().sorted().toList()) {
            prodDist.addEntry(k, prodFields.get(k));
        }

        Distribution testDist = new Distribution("Field visibility test code");
        testDist.addEntry("STATIC", testStatic);
        testDist.addEntry("FINAL", testFinal);
        testDist.addEntry("TOTAL", testTotal);
        for (String k : testFields.keySet().stream().sorted().toList()) {
            testDist.addEntry(k, testFields.get(k));
        }

        Distribution genDist = new Distribution("Field visibility generated code");
        genDist.addEntry("STATIC", genStatic);
        genDist.addEntry("FINAL", genFinal);
        genDist.addEntry("TOTAL", genTotal);
        for (String k : genFields.keySet().stream().sorted().toList()) {
            genDist.addEntry(k, genFields.get(k));
        }

        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "FieldVisibility/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodFields, testFields, genFields);
        return List.of(prodDist, testDist, genDist);
    }

    @Tool(name = "java_get_class_cohesion_report",
            value =
                    """
                            Returns a report regarding the class cohesion metrics in the module.
                            """)
    public List<Distribution> getClassCohesionReport(@P("The name of the module to analyse")
                                                      String moduleName) throws IOException {
        logger.info("## GetClassCohesionReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Field count per class
        Map<Integer, Integer> prodFieldCount = new HashMap<>();
        Map<Integer, Integer> testFieldCount = new HashMap<>();
        Map<Integer, Integer> genFieldCount = new HashMap<>();
        // Method-to-field ratio: key is ratio rounded to int
        Map<Integer, Integer> prodRatio = new HashMap<>();
        Map<Integer, Integer> testRatio = new HashMap<>();
        Map<Integer, Integer> genRatio = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                Map<Integer, Integer> tgtFc, tgtRatio;

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    tgtFc = prodFieldCount; tgtRatio = prodRatio;
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    tgtFc = testFieldCount; tgtRatio = testRatio;
                } else {
                    tgtFc = genFieldCount; tgtRatio = genRatio;
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    int fieldCount = clazz.getFields().size();
                    int methodCount = clazz.getMethods().size();
                    tgtFc.merge(fieldCount, 1, Integer::sum);
                    if (methodCount > 0) {
                        int ratio = (fieldCount * 100) / methodCount;
                        tgtRatio.merge(ratio, 1, Integer::sum);
                    }
                }
            }
        }

        Distribution prodFcDist = new Distribution("Field count production code");
        for (int c : prodFieldCount.keySet().stream().sorted().toList()) {
            prodFcDist.addEntry(Integer.toString(c), prodFieldCount.get(c));
        }
        Distribution testFcDist = new Distribution("Field count test code");
        for (int c : testFieldCount.keySet().stream().sorted().toList()) {
            testFcDist.addEntry(Integer.toString(c), testFieldCount.get(c));
        }
        Distribution genFcDist = new Distribution("Field count generated code");
        for (int c : genFieldCount.keySet().stream().sorted().toList()) {
            genFcDist.addEntry(Integer.toString(c), genFieldCount.get(c));
        }

        Distribution prodRatioDist = new Distribution("Field-to-method ratio % production code");
        for (int r : prodRatio.keySet().stream().sorted().toList()) {
            prodRatioDist.addEntry(Integer.toString(r), prodRatio.get(r));
        }
        Distribution testRatioDist = new Distribution("Field-to-method ratio % test code");
        for (int r : testRatio.keySet().stream().sorted().toList()) {
            testRatioDist.addEntry(Integer.toString(r), testRatio.get(r));
        }
        Distribution genRatioDist = new Distribution("Field-to-method ratio % generated code");
        for (int r : genRatio.keySet().stream().sorted().toList()) {
            genRatioDist.addEntry(Integer.toString(r), genRatio.get(r));
        }

                CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "FieldCountPerClass/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodFieldCount, testFieldCount, genFieldCount);
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "FieldToMethodRatio/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodRatio, testRatio, genRatio);
return List.of(prodFcDist, testFcDist, genFcDist,
                prodRatioDist, testRatioDist, genRatioDist);
    }



    @Tool(name = "java_get_coupling_report",
            value =
                    """
                            Returns a report regarding the class coupling (efferent coupling) in the module.
                            """)
    public List<Distribution> getCouplingReport(@P("The name of the module to analyse")
                                                 String moduleName) throws IOException {
        logger.info("## GetCouplingReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Class-level efferent coupling distribution
        Map<Integer, Integer> prodCc = new HashMap<>();
        Map<Integer, Integer> testCc = new HashMap<>();
        Map<Integer, Integer> genCc = new HashMap<>();
        // Module-level dependency counts: external module -> count of imports
        Map<String, Integer> prodDep = new HashMap<>();
        Map<String, Integer> testDep = new HashMap<>();
        Map<String, Integer> genDep = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                Map<Integer, Integer> targetCc;
                Map<String, Integer> targetDep;

                if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetCc = prodCc; targetDep = prodDep;
                } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                    targetCc = testCc; targetDep = testDep;
                } else {
                    targetCc = genCc; targetDep = genDep;
                }

                // Collect all unique external imports from this BuildUnit
                // An import is external if it does not start with the same package prefix as the class
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    String qualifiedName = clazz.getQualifiedName();
                    String packageName = "";
                    int lastDot = qualifiedName.lastIndexOf('.');
                    if (lastDot >= 0) {
                        packageName = qualifiedName.substring(0, lastDot);
                    }

                    int externalCount = 0;
                    for (String imp : buildUnit.getImports()) {
                        // Skip static imports, same-package, and java.lang
                        if (imp.startsWith("static ")) continue;
                        if (imp.equals(packageName) || imp.startsWith(packageName + ".")) continue;
                        if (imp.startsWith("java.lang")) continue;
                        externalCount++;

                        // Track external module (first 2 segments of package)
                        int firstDot = imp.indexOf('.');
                        if (firstDot >= 0) {
                            int secondDot = imp.indexOf('.', firstDot + 1);
                            String moduleKey = secondDot >= 0
                                    ? imp.substring(0, secondDot) : imp;
                            targetDep.merge(moduleKey, 1, Integer::sum);
                        }
                    }

                    targetCc.merge(externalCount, 1, Integer::sum);
                }
            }
        }

        Distribution prodCcDist = new Distribution("Efferent coupling production code");
        for (int c : prodCc.keySet().stream().sorted().toList()) {
            prodCcDist.addEntry(Integer.toString(c), prodCc.get(c));
        }
        Distribution testCcDist = new Distribution("Efferent coupling test code");
        for (int c : testCc.keySet().stream().sorted().toList()) {
            testCcDist.addEntry(Integer.toString(c), testCc.get(c));
        }
        Distribution genCcDist = new Distribution("Efferent coupling generated code");
        for (int c : genCc.keySet().stream().sorted().toList()) {
            genCcDist.addEntry(Integer.toString(c), genCc.get(c));
        }

        Distribution prodDepDist = new Distribution("Module dependencies production code");
        for (String k : prodDep.keySet().stream().sorted().toList()) {
            prodDepDist.addEntry(k, prodDep.get(k));
        }
        Distribution testDepDist = new Distribution("Module dependencies test code");
        for (String k : testDep.keySet().stream().sorted().toList()) {
            testDepDist.addEntry(k, testDep.get(k));
        }
        Distribution genDepDist = new Distribution("Module dependencies generated code");
        for (String k : genDep.keySet().stream().sorted().toList()) {
            genDepDist.addEntry(k, genDep.get(k));
        }

                CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "ClassCoupling/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodCc, testCc, genCc);
        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "ModuleDependencies/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodDep, testDep, genDep);
return List.of(prodCcDist, testCcDist, genCcDist,
                prodDepDist, testDepDist, genDepDist);
    }



    @Tool(name = "java_get_test_coverage_report",
            value =
                    """
                            Returns a report regarding test coverage in the module.
                            """)
    public List<Distribution> getTestCoverageReport(@P("The name of the module to analyse")
                                                     String moduleName) throws IOException {
        logger.info("## GetTestCoverageReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Collect production and test class names
        Set<String> prodClassNames = new HashSet<>();
        Set<String> testClassNames = new HashSet<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    String simpleName = clazz.getQualifiedName();
                    int lastDot = simpleName.lastIndexOf('.');
                    String className = lastDot >= 0 ? simpleName.substring(lastDot + 1) : simpleName;

                    if (buildUnit.isProduction() && !buildUnit.isGenerated()) {
                        // Skip abstract classes
                        if (!className.contains("Abstract")) {
                            prodClassNames.add(className);
                        }
                    } else if (!buildUnit.isProduction() && !buildUnit.isGenerated()) {
                        testClassNames.add(className);
                    }
                }
            }
        }

        // Match production classes to test classes
        int total = prodClassNames.size();
        int tested = 0;
        int untested = 0;
        List<String> untestedList = new LinkedList<>();

        for (String prodName : prodClassNames) {
            boolean hasTest = testClassNames.contains(prodName + "Test")
                    || testClassNames.contains(prodName + "Tests");
            if (hasTest) {
                tested++;
            } else {
                untested++;
                untestedList.add(prodName);
            }
        }

        Distribution coverageDist = new Distribution("Test coverage summary");
        coverageDist.addEntry("TOTAL_PRODUCTION_CLASSES", total);
        coverageDist.addEntry("TESTED_CLASSES", tested);
        coverageDist.addEntry("UNTESTED_CLASSES", untested);
        if (total > 0) {
            coverageDist.addEntry("COVERAGE_RATIO_PERCENT", (tested * 100) / total);
        }

        Distribution untestedDist = new Distribution("Untested production classes");
        for (String name : untestedList) {
            untestedDist.addEntry(name, 1);
        }

        java.util.Map<String, Integer> tcData = new java.util.HashMap<>();
        tcData.put("TESTED_PRODUCTION", tested);
        tcData.put("UNTESTED_PRODUCTION", untested);
        tcData.put("TOTAL_PRODUCTION", total);
        CsvReportWriter.writeMapCsv(context.getWorkingDirectory(), "TestCoverage/" + sanitizeModuleName(moduleName) + ".csv", tcData);
        return List.of(coverageDist, untestedDist);
    }



    @Tool(name = "java_get_circular_dependency_report",
            value =
                    """
                            Returns a report regarding circular dependencies in the module.
                            """)
    public List<String> getCircularDependencyReport(@P("The name of the module to analyse")
                                                     String moduleName) throws IOException {
        logger.info("## GetCircularDependencyReport('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return List.of();
        }

        Module module = getModuleReport(moduleName);

        // Build directed graph: class simple name -> set of external class names it imports
        Map<String, Set<String>> graph = new HashMap<>();
        // Track which class belongs to which BuildUnit for packaging info
        Map<String, String> classPackage = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                for (Clazz clazz : buildUnit.getClazzes()) {
                    String qualifiedName = clazz.getQualifiedName();
                    int lastDot = qualifiedName.lastIndexOf('.');
                    String simpleName = lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
                    String packageName = lastDot >= 0 ? qualifiedName.substring(0, lastDot) : "";

                    classPackage.put(simpleName, packageName);
                    graph.putIfAbsent(simpleName, new HashSet<>());

                    // Build edges: for each import, check if it resolves to a class in this module
                    for (String imp : buildUnit.getImports()) {
                        if (imp.startsWith("static ")) continue;
                        if (imp.startsWith("java.lang")) continue;

                        String importSimpleName = imp.substring(imp.lastIndexOf('.') + 1);

                        // Only consider imports that reference another class in this module
                        if (!importSimpleName.equals(simpleName)
                                && graph.containsKey(importSimpleName)) {
                            graph.get(simpleName).add(importSimpleName);
                        }
                    }
                }
            }
        }

        // Tarjan's SCC algorithm
        List<List<String>> cycles = findCycles(graph);

        // Format result as list of strings
        List<String> result = new LinkedList<>();
        if (cycles.isEmpty()) {
            result.add("No circular dependencies found.");
        } else {
            result.add("Found " + cycles.size() + " cycle(s):");
            for (int i = 0; i < cycles.size(); i++) {
                List<String> cycle = cycles.get(i);
                result.add("Cycle " + (i + 1) + " (" + cycle.size() + " classes): "
                        + String.join(" -> ", cycle) + " -> " + cycle.get(0));
            }
        }
        CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "CircularDeps/" + sanitizeModuleName(moduleName) + ".csv", "CycleInfo", result);
        return result;
    }

    private List<List<String>> findCycles(Map<String, Set<String>> graph) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowlink = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<List<String>> sccs = new LinkedList<>();
        int[] nextIndex = {0};

        for (String node : graph.keySet()) {
            if (!index.containsKey(node)) {
                strongConnect(node, graph, index, lowlink, stack, onStack, sccs, nextIndex);
            }
        }

        // Filter SCCs of size > 1 (those contain cycles)
        List<List<String>> cycles = new LinkedList<>();
        for (List<String> scc : sccs) {
            if (scc.size() > 1) {
                cycles.add(scc);
            }
        }
        return cycles;
    }

    private void strongConnect(String node, Map<String, Set<String>> graph,
                                Map<String, Integer> index, Map<String, Integer> lowlink,
                                Deque<String> stack, Set<String> onStack,
                                List<List<String>> sccs, int[] nextIndex) {
        index.put(node, nextIndex[0]);
        lowlink.put(node, nextIndex[0]);
        nextIndex[0]++;
        stack.push(node);
        onStack.add(node);

        for (String neighbor : graph.getOrDefault(node, Set.of())) {
            if (!index.containsKey(neighbor)) {
                strongConnect(neighbor, graph, index, lowlink, stack, onStack, sccs, nextIndex);
                lowlink.put(node, Math.min(lowlink.get(node), lowlink.get(neighbor)));
            } else if (onStack.contains(neighbor)) {
                lowlink.put(node, Math.min(lowlink.get(node), index.get(neighbor)));
            }
        }

        if (lowlink.get(node).equals(index.get(node))) {
            List<String> scc = new LinkedList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(node));
            sccs.add(scc);
        }
    }



    @Tool(name = "java_get_method_count_report",
            value =
                    """
                            Returns the distribution of method count per class in the module.
                            """)
    public List<Distribution> getMethodCountReport(@P("The name of the module to analyse")
                                                    String moduleName) throws IOException {
        logger.info("## GetMethodCountReport('{}')", moduleName);
        if (moduleName == null || moduleName.isEmpty()) { logger.warn("No module name"); return List.of(); }
        Module module = getModuleReport(moduleName);
        Map<Integer, Integer> prod = new HashMap<>(), test = new HashMap<>(), gen = new HashMap<>();
        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                Map<Integer, Integer> t = bu.isProduction() && !bu.isGenerated() ? prod :
                    !bu.isProduction() && !bu.isGenerated() ? test : gen;
                for (Clazz clazz : bu.getClazzes()) {
                    t.merge(clazz.getMethods().size(), 1, Integer::sum);
                }
            }
        }
        Distribution pd = new Distribution("Method count production");
        for (int k : prod.keySet().stream().sorted().toList()) pd.addEntry(Integer.toString(k), prod.get(k));
        Distribution td = new Distribution("Method count test");
        for (int k : test.keySet().stream().sorted().toList()) td.addEntry(Integer.toString(k), test.get(k));
        Distribution gd = new Distribution("Method count generated");
        for (int k : gen.keySet().stream().sorted().toList()) gd.addEntry(Integer.toString(k), gen.get(k));
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "MethodCount/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prod, test, gen);
return List.of(pd, td, gd);
    }

    @Tool(name = "java_get_documentation_ratio_report",
            value =
                    """
                            Returns documentation coverage statistics for the module.
                            """)
    public List<Distribution> getDocumentationRatioReport(@P("The name of the module to analyse")
                                                           String moduleName) throws IOException {
        logger.info("## GetDocumentationRatioReport('{}')", moduleName);
        if (moduleName == null || moduleName.isEmpty()) { logger.warn("No module name"); return List.of(); }
        Module module = getModuleReport(moduleName);
        int prodCls=0, prodDocCls=0, prodMth=0, prodDocMth=0;
        int testCls=0, testDocCls=0, testMth=0, testDocMth=0;
        int genCls=0, genDocCls=0, genMth=0, genDocMth=0;
        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                boolean isProd = bu.isProduction() && !bu.isGenerated();
                boolean isTest = !bu.isProduction() && !bu.isGenerated();
                for (Clazz clazz : bu.getClazzes()) {
                    if (isProd) { prodCls++; if (hasDoc(clazz.getDocumentation())) prodDocCls++; }
                    else if (isTest) { testCls++; if (hasDoc(clazz.getDocumentation())) testDocCls++; }
                    else { genCls++; if (hasDoc(clazz.getDocumentation())) genDocCls++; }
                    for (Method m : clazz.getMethods()) {
                        if (isProd) { prodMth++; if (hasDoc(m.getDocumentation())) prodDocMth++; }
                        else if (isTest) { testMth++; if (hasDoc(m.getDocumentation())) testDocMth++; }
                        else { genMth++; if (hasDoc(m.getDocumentation())) genDocMth++; }
                    }
                }
            }
        }
        Distribution pd = new Distribution("Documentation production");
        pd.addEntry("DOCUMENTED_CLASSES", prodDocCls); pd.addEntry("TOTAL_CLASSES", prodCls);
        pd.addEntry("DOCUMENTED_METHODS", prodDocMth); pd.addEntry("TOTAL_METHODS", prodMth);
        Distribution td = new Distribution("Documentation test");
        td.addEntry("DOCUMENTED_CLASSES", testDocCls); td.addEntry("TOTAL_CLASSES", testCls);
        td.addEntry("DOCUMENTED_METHODS", testDocMth); td.addEntry("TOTAL_METHODS", testMth);
        Distribution gd = new Distribution("Documentation generated");
        gd.addEntry("DOCUMENTED_CLASSES", genDocCls); gd.addEntry("TOTAL_CLASSES", genCls);
        gd.addEntry("DOCUMENTED_METHODS", genDocMth); gd.addEntry("TOTAL_METHODS", genMth);
                java.util.Map<String, Integer> docData = new java.util.HashMap<>();
        docData.put("PROD_CLASSES", prodCls);
        docData.put("PROD_DOC_CLASSES", prodDocCls);
        docData.put("PROD_METHODS", prodMth);
        docData.put("PROD_DOC_METHODS", prodDocMth);
        CsvReportWriter.writeMapCsv(context.getWorkingDirectory(), "DocumentationRatio/" + sanitizeModuleName(moduleName) + ".csv", docData);
return List.of(pd, td, gd);
    }

    private static boolean hasDoc(String doc) {
        return doc != null && !doc.trim().isEmpty();
    }

    @Tool(name = "java_get_data_class_report",
            value =
                    """
                            Identifies data class candidates in the module.
                            """)
    public List<String> getDataClassReport(@P("The name of the module to analyse")
                                            String moduleName) throws IOException {
        logger.info("## GetDataClassReport('{}')", moduleName);
        if (moduleName == null || moduleName.isEmpty()) { logger.warn("No module name"); return List.of(); }
        Module module = getModuleReport(moduleName);
        List<String> result = new LinkedList<>();
        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                for (Clazz clazz : bu.getClazzes()) {
                    if (isDataClass(clazz)) {
                        result.add(clazz.getQualifiedName());
                    }
                }
            }
        }
                CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "DataClasses/" + sanitizeModuleName(moduleName) + ".csv", "ClassName", result);
        if (result.isEmpty()) result.add("No data class candidates found.");
        return result;
    }

    private boolean isDataClass(Clazz clazz) {
        List<Field> fields = clazz.getFields();
        List<Method> methods = clazz.getMethods();
        if (fields.isEmpty()) return false;

        boolean allPrivate = fields.stream().allMatch(f -> f.getVisibility() == MethodVisibility.PRIVATE);
        boolean allPublic = fields.stream().allMatch(f -> f.getVisibility() == MethodVisibility.PUBLIC);
        if (!allPrivate && !allPublic) return false;

        if (allPrivate) {
            for (Method m : methods) {
                String name = m.getName();
                if (name.equals("<init>") || name.equals("<clinit>")) continue;
                if (!name.startsWith("get") && !name.startsWith("set") && !name.startsWith("is")) {
                    return false;
                }
            }
            return true;
        }

        // All fields public: check for non-trivial methods
        if (allPublic) {
            for (Method m : methods) {
                String name = m.getName();
                if (name.equals("<init>") || name.equals("<clinit>")) continue;
                if (name.equals("toString") || name.equals("hashCode") || name.equals("equals")) continue;
                return false; // has business logic
            }
            return true;
        }
        return false;
    }

    @Tool(name = "java_get_boolean_parameter_report",
            value =
                    """
                            Identifies methods with excessive boolean parameters in the module.
                            """)
    public List<String> getBooleanParameterReport(@P("The name of the module to analyse")
                                                   String moduleName) throws IOException {
        logger.info("## GetBooleanParameterReport('{}')", moduleName);
        if (moduleName == null || moduleName.isEmpty()) { logger.warn("No module name"); return List.of(); }
        Module module = getModuleReport(moduleName);
        List<String> result = new LinkedList<>();
        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                for (Clazz clazz : bu.getClazzes()) {
                    for (Method m : clazz.getMethods()) {
                        String desc = m.getDescriptor();
                        if (desc != null && countBooleanParams(desc) >= 3) {
                            result.add(clazz.getQualifiedName() + "." + m.getName());
                        }
                    }
                }
            }
        }
                CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "BooleanParamFlags/" + sanitizeModuleName(moduleName) + ".csv", "Method", result);
        if (result.isEmpty()) result.add("No methods with 3+ boolean parameters found.");
        return result;
    }

    private static int countBooleanParams(String descriptor) {
        // Descriptor format: (paramTypes)returnType
        int paren = descriptor.indexOf(')');
        if (paren < 0) return 0;
        String params = descriptor.substring(1, paren);
        int count = 0;
        for (int i = 0; i < params.length(); i++) {
            if (params.charAt(i) == 'Z') count++;
        }
        return count;
    }

    @Tool(name = "java_get_annotation_report",
            value =
                    """
                            Returns a report regarding the annotation usage in the module.
                            """)
    public List<Distribution> getAnnotationReport(@P("The name of the module to analyse")
                                                   String moduleName) throws IOException {
        logger.info("## GetAnnotationReport('{}')", moduleName);
        if (moduleName == null || moduleName.isEmpty()) { logger.warn("No module name given"); return List.of(); }
        Module module = getModuleReport(moduleName);

        Map<String, Integer> prodAnnotations = new HashMap<>();
        Map<String, Integer> testAnnotations = new HashMap<>();
        Map<String, Integer> genAnnotations = new HashMap<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                Map<String, Integer> target;
                if (bu.isProduction() && !bu.isGenerated()) {
                    target = prodAnnotations;
                } else if (!bu.isProduction() && !bu.isGenerated()) {
                    target = testAnnotations;
                } else {
                    target = genAnnotations;
                }

                for (Clazz clazz : bu.getClazzes()) {
                    for (Annotation ann : clazz.getAnnotations()) {
                        String key = ann.getQualifiedName() != null ? ann.getQualifiedName() : ann.getName();
                        target.merge(key, 1, Integer::sum);
                    }
                    for (Method method : clazz.getMethods()) {
                        for (Annotation ann : method.getAnnotations()) {
                            String key = ann.getQualifiedName() != null ? ann.getQualifiedName() : ann.getName();
                            target.merge(key, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        Distribution prodDist = new Distribution("Annotation distribution production code");
        for (String k : prodAnnotations.keySet().stream().sorted().toList()) {
            prodDist.addEntry(k, prodAnnotations.get(k));
        }

        Distribution testDist = new Distribution("Annotation distribution test code");
        for (String k : testAnnotations.keySet().stream().sorted().toList()) {
            testDist.addEntry(k, testAnnotations.get(k));
        }

        Distribution genDist = new Distribution("Annotation distribution generated code");
        for (String k : genAnnotations.keySet().stream().sorted().toList()) {
            genDist.addEntry(k, genAnnotations.get(k));
        }

        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "AnnotationDensity/" + sanitizeModuleName(moduleName) + ".csv",
                new String[]{"production","test","generated"},
                prodAnnotations, testAnnotations, genAnnotations);
        return List.of(prodDist, testDist, genDist);
    }

    @Tool(name = "java_get_package_tangle_report",
            value =
                    """
                            Returns a report regarding package-level circular dependencies in the module.
                            """)
    public List<String> getPackageTangleReport(@P("The name of the module to analyse")
                                                 String moduleName) throws IOException {
        logger.info("## GetPackageTangleReport('{}')", moduleName);
        if (moduleName == null || moduleName.isEmpty()) { logger.warn("No module name given"); return List.of(); }
        Module module = getModuleReport(moduleName);

        // Build set of all packages in this module + map of simpleName -> packages
        Set<String> modulePackages = new HashSet<>();
        Map<String, Set<String>> simpleNamePackages = new HashMap<>();
        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                for (Clazz clazz : bu.getClazzes()) {
                    String qn = clazz.getQualifiedName();
                    int lastDot = qn.lastIndexOf('.');
                    String pkg = lastDot >= 0 ? qn.substring(0, lastDot) : "";
                    modulePackages.add(pkg);
                    String simpleName = lastDot >= 0 ? qn.substring(lastDot + 1) : qn;
                    simpleNamePackages.computeIfAbsent(simpleName, k -> new HashSet<>()).add(pkg);
                }
            }
        }

        // Build package-level graph: edge from sourcePkg to impPkg if any class in sourcePkg imports from impPkg
        Map<String, Set<String>> graph = new HashMap<>();
        // Track which class-level imports contribute to each package-level edge
        Map<String, Set<String>> edgeDetails = new HashMap<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                for (Clazz clazz : bu.getClazzes()) {
                    String qn = clazz.getQualifiedName();
                    int lastDot = qn.lastIndexOf('.');
                    String sourcePkg = lastDot >= 0 ? qn.substring(0, lastDot) : "";
                    String simpleName = lastDot >= 0 ? qn.substring(lastDot + 1) : qn;

                    graph.putIfAbsent(sourcePkg, new HashSet<>());

                    for (String imp : bu.getImports()) {
                        if (imp.startsWith("static ")) continue;
                        if (imp.startsWith("java.lang")) continue;

                        int impDot = imp.lastIndexOf('.');
                        if (impDot < 0) continue;
                        String impPkg = imp.substring(0, impDot);

                        // Only create edges between packages within this module
                        if (modulePackages.contains(impPkg) && !sourcePkg.equals(impPkg)) {
                            graph.get(sourcePkg).add(impPkg);
                            String edgeKey = sourcePkg + "|" + impPkg;
                            edgeDetails.computeIfAbsent(edgeKey, k -> new TreeSet<>())
                                    .add(simpleName + " -> " + imp.substring(impDot + 1));
                        }
                    }
                }
            }
        }

        // Run Tarjan's SCC on package-level graph (reusing findCycles)
        List<List<String>> cycles = findCycles(graph);

        List<String> result = new LinkedList<>();
        if (cycles.isEmpty()) {
            result.add("No package-level circular dependencies found.");
        } else {
            result.add("Found " + cycles.size() + " package-level cycle(s):");
            for (int i = 0; i < cycles.size(); i++) {
                List<String> cycle = cycles.get(i);
                result.add("Cycle " + (i + 1) + " (" + cycle.size() + " packages): "
                        + String.join(" -> ", cycle) + " -> " + cycle.get(0));
                result.add("  Contributing class dependencies:");
                for (int j = 0; j < cycle.size(); j++) {
                    String fromPkg = cycle.get(j);
                    String toPkg = cycle.get((j + 1) % cycle.size());
                    Set<String> details = edgeDetails.get(fromPkg + "|" + toPkg);
                    if (details != null) {
                        for (String detail : details) {
                            result.add("    " + fromPkg + "." + detail);
                        }
                    }
                }
            }
        }

        CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "PackageTangles/" + sanitizeModuleName(moduleName) + ".csv", "PackageCycleInfo", result);
        return result;
    }

    @Tool(name = "java_get_import_diversity_report",
            value =
                    """
                            Returns a report regarding import diversity in the module.
                            """)
    public List<Distribution> getImportDiversityReport(@P("The name of the module to analyse")
                                                        String moduleName) throws IOException {
        logger.info("## GetImportDiversityReport('{}')", moduleName);
        if (moduleName == null || moduleName.isEmpty()) { logger.warn("No module name given"); return List.of(); }
        Module module = getModuleReport(moduleName);

        final String projectNamespace = determineProjectNamespace(module);

        Map<String, Integer> prodImports = new HashMap<>();
        Map<String, Integer> testImports = new HashMap<>();
        Map<String, Integer> genImports = new HashMap<>();

        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                Map<String, Integer> target;
                if (bu.isProduction() && !bu.isGenerated()) {
                    target = prodImports;
                } else if (!bu.isProduction() && !bu.isGenerated()) {
                    target = testImports;
                } else {
                    target = genImports;
                }

                for (String imp : bu.getImports()) {
                    if (imp.startsWith("static ")) continue;
                    if (imp.startsWith("java.lang")) continue;

                    // Extract top-level/second-level prefix
                    String[] parts = imp.split("\\.");
                    String prefix;
                    if (parts.length >= 2) {
                        prefix = parts[0] + "." + parts[1];
                    } else if (parts.length == 1) {
                        prefix = parts[0];
                    } else {
                        continue;
                    }
                    target.merge(prefix, 1, Integer::sum);
                }
            }
        }

        // Build distributions: separate for external vs internal, plus overall prefix counts
        Distribution prodDist = new Distribution("Import distribution production code");
        prodImports.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    String label = e.getKey() + (isInternal(e.getKey(), projectNamespace) ? " (internal)" : " (external)");
                    prodDist.addEntry(label, e.getValue());
                });

        Distribution testDist = new Distribution("Import distribution test code");
        testImports.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    String label = e.getKey() + (isInternal(e.getKey(), projectNamespace) ? " (internal)" : " (external)");
                    testDist.addEntry(label, e.getValue());
                });

        Distribution genDist = new Distribution("Import distribution generated code");
        genImports.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    String label = e.getKey() + (isInternal(e.getKey(), projectNamespace) ? " (internal)" : " (external)");
                    genDist.addEntry(label, e.getValue());
                });

        // Write CSV with category, prefix, count columns
        Map<String, Integer> allImports = new HashMap<>();
        for (Map.Entry<String, Integer> e : prodImports.entrySet()) {
            allImports.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : testImports.entrySet()) {
            allImports.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : genImports.entrySet()) {
            allImports.merge(e.getKey(), e.getValue(), Integer::sum);
        }

        CsvReportWriter.writeMapCsv(context.getWorkingDirectory(), "ImportDiversity/" + sanitizeModuleName(moduleName) + ".csv", allImports);

        return List.of(prodDist, testDist, genDist);
    }

    private boolean isInternal(String prefix, String projectNamespace) {
        if (projectNamespace == null) return false;
        return prefix.equals(projectNamespace) || projectNamespace.startsWith(prefix);
    }

    private String determineProjectNamespace(Module module) {
        for (Package pck : module.getPackages()) {
            for (BuildUnit bu : pck.getBuildUnits()) {
                for (Clazz clazz : bu.getClazzes()) {
                    String qn = clazz.getQualifiedName();
                    int lastDot = qn.lastIndexOf('.');
                    if (lastDot >= 0) {
                        return qn.substring(0, lastDot);
                    }
                }
            }
        }
        return null;
    }
    @Tool(name = "java_get_all_cyclomatic_complexity_reports",
            value = """
                    Returns compact cyclomatic complexity distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllCyclomaticComplexityReports() throws IOException {
        return getAllModuleMetricReports("Cyclomatic complexity", this::getCyclomaticComplexityModuleReport);
    }

    @Tool(name = "java_get_all_visibility_distribution_reports",
            value = """
                    Returns compact method visibility distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllVisibilityDistributionReports() throws IOException {
        return getAllModuleMetricReports("Method visibility distribution", this::getVisibilityDistributionReport);
    }

    @Tool(name = "java_get_all_inheritance_reports",
            value = """
                    Returns compact inheritance pattern distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllInheritanceReports() throws IOException {
        return getAllModuleMetricReports("Inheritance", this::getInheritanceReport);
    }

    @Tool(name = "java_get_all_method_complexity_reports",
            value = """
                    Returns compact method complexity distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllMethodComplexityReports() throws IOException {
        return getAllModuleMetricReports("Method complexity", this::getMethodComplexityReport);
    }

    @Tool(name = "java_get_all_method_nesting_depth_reports",
            value = """
                    Returns compact method nesting depth distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllMethodNestingDepthReports() throws IOException {
        return getAllModuleMetricReports("Method nesting depth", this::getMethodNestingDepthReport);
    }

    @Tool(name = "java_get_all_field_visibility_reports",
            value = """
                    Returns compact field visibility distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllFieldVisibilityReports() throws IOException {
        return getAllModuleMetricReports("Field visibility distribution", this::getFieldVisibilityReport);
    }

    @Tool(name = "java_get_all_class_cohesion_reports",
            value = """
                    Returns compact class cohesion distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllClassCohesionReports() throws IOException {
        return getAllModuleMetricReports("Class cohesion", this::getClassCohesionReport);
    }

    @Tool(name = "java_get_all_coupling_reports",
            value = """
                    Returns compact coupling distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllCouplingReports() throws IOException {
        return getAllModuleMetricReports("Coupling", this::getCouplingReport);
    }

    @Tool(name = "java_get_all_test_coverage_reports",
            value = """
                    Returns compact test coverage distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllTestCoverageReports() throws IOException {
        return getAllModuleMetricReports("Test coverage", this::getTestCoverageReport);
    }

    @Tool(name = "java_get_all_circular_dependency_reports",
            value = """
                    Returns compact circular dependency findings for all detected Java modules.
                    """)
    public Map<String, Object> getAllCircularDependencyReports() throws IOException {
        return getAllModuleMetricReports("Circular dependency", this::getCircularDependencyReport);
    }

    @Tool(name = "java_get_all_method_count_reports",
            value = """
                    Returns compact method count distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllMethodCountReports() throws IOException {
        return getAllModuleMetricReports("Method count", this::getMethodCountReport);
    }

    @Tool(name = "java_get_all_documentation_ratio_reports",
            value = """
                    Returns compact documentation ratio distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllDocumentationRatioReports() throws IOException {
        return getAllModuleMetricReports("Documentation ratio", this::getDocumentationRatioReport);
    }

    @Tool(name = "java_get_all_data_class_reports",
            value = """
                    Returns compact data class candidate findings for all detected Java modules.
                    """)
    public Map<String, Object> getAllDataClassReports() throws IOException {
        return getAllModuleMetricReports("Data class candidates", this::getDataClassReport);
    }

    @Tool(name = "java_get_all_boolean_parameter_reports",
            value = """
                    Returns compact boolean parameter findings for all detected Java modules.
                    """)
    public Map<String, Object> getAllBooleanParameterReports() throws IOException {
        return getAllModuleMetricReports("Boolean parameters", this::getBooleanParameterReport);
    }

    @Tool(name = "java_get_all_annotation_reports",
            value = """
                    Returns compact annotation usage distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllAnnotationReports() throws IOException {
        return getAllModuleMetricReports("Annotation usage", this::getAnnotationReport);
    }

    @Tool(name = "java_get_all_package_tangle_reports",
            value = """
                    Returns compact package tangle findings for all detected Java modules.
                    """)
    public Map<String, Object> getAllPackageTangleReports() throws IOException {
        return getAllModuleMetricReports("Package tangles", this::getPackageTangleReport);
    }

    @Tool(name = "java_get_all_import_diversity_reports",
            value = """
                    Returns compact import diversity distributions for all detected Java modules.
                    """)
    public Map<String, Object> getAllImportDiversityReports() throws IOException {
        return getAllModuleMetricReports("Import diversity", this::getImportDiversityReport);
    }

    @Tool(name = "java_get_inter_module_dependency_report",
           value = """
                   Analyses all modules and their imports to determine inter-module dependencies.
                   Returns Ce-inter, Ca (afferent coupling), and Instability per module.
                   Also writes InterModuleDependencyMatrix.csv with the full dependency matrix.
                   """)
    public List<Distribution> getInterModuleDependencyReport() throws IOException {
        logger.info("## GetInterModuleDependencyReport");

        ObjectNode analysisState = context.getAnalysisState();
        JsonNode modulesSection = analysisState.get("modules");

        if (modulesSection == null || modulesSection.isNull()) {
            logger.warn("No modules section in analysis state");
            return List.of();
        }

        JsonNode modulesArray = modulesSection.get("modules");

        if (modulesArray == null || !modulesArray.isArray() || modulesArray.isEmpty()) {
            logger.warn("No modules found in analysis state");
            return List.of();
        }

        // Step 1: Collect all module names
        List<String> moduleNames = new LinkedList<>();
        for (JsonNode moduleNode : modulesArray) {
            moduleNames.add(moduleNode.get("name").asText());
        }

        if (moduleNames.size() < 2) {
            logger.info("Fewer than 2 modules, skipping inter-module analysis");
            return List.of();
        }

        // Step 2: Load all module reports
        Map<String, Module> allModules = new HashMap<>();
        for (String name : moduleNames) {
            try {
                allModules.put(name, getModuleReport(name));
            } catch (IOException e) {
                logger.warn("Could not load module report for '{}': {}", name, e.getMessage());
            }
        }

        // Step 3: Build namespace index per module
        Map<String, Set<String>> moduleClassNames = new HashMap<>();
        Map<String, Set<String>> modulePackages = new HashMap<>();

        for (Map.Entry<String, Module> entry : allModules.entrySet()) {
            String modName = entry.getKey();
            Module mod = entry.getValue();
            Set<String> classNames = new HashSet<>();
            Set<String> pkgSet = new HashSet<>();

            for (Package pck : mod.getPackages()) {
                for (BuildUnit bu : pck.getBuildUnits()) {
                    for (Clazz clazz : bu.getClazzes()) {
                        String qn = clazz.getQualifiedName();
                        classNames.add(qn);
                        int lastDot = qn.lastIndexOf('.');
                        if (lastDot >= 0) {
                            pkgSet.add(qn.substring(0, lastDot));
                        }
                    }
                }
            }

            moduleClassNames.put(modName, classNames);
            modulePackages.put(modName, pkgSet);
        }

        // Step 4: For each module, classify its imports
        Map<String, Map<String, Integer>> dependencyMatrix = new HashMap<>();

        for (Map.Entry<String, Module> entry : allModules.entrySet()) {
            String fromModule = entry.getKey();
            Module mod = entry.getValue();
            Map<String, Integer> depCounts = new HashMap<>();

            for (Package pck : mod.getPackages()) {
                for (BuildUnit bu : pck.getBuildUnits()) {
                    for (String imp : bu.getImports()) {
                        if (imp.startsWith("static ")) continue;
                        if (imp.startsWith("java.lang")) continue;
                        if (imp.startsWith("java.") || imp.startsWith("javax.")) continue;

                        String targetModule = findTargetModule(imp, fromModule, moduleClassNames, modulePackages);
                        if (targetModule != null) {
                            depCounts.merge(targetModule, 1, Integer::sum);
                        }
                    }
                }
            }

            dependencyMatrix.put(fromModule, depCounts);
        }

        // Step 5: Compute Ce-inter, Ca, Instability
        Map<String, Integer> ceInter = new HashMap<>();
        Map<String, Integer> ca = new HashMap<>();

        List<String> allModuleNames = new ArrayList<>(moduleNames);
        Collections.sort(allModuleNames);

        for (String modName : allModuleNames) {
            Map<String, Integer> deps = dependencyMatrix.getOrDefault(modName, new HashMap<>());
            int uniqueDeps = 0;
            for (String target : deps.keySet()) {
                if (!target.equals(modName)) {
                    uniqueDeps++;
                }
            }
            ceInter.put(modName, uniqueDeps);
        }

        for (Map.Entry<String, Map<String, Integer>> entry : dependencyMatrix.entrySet()) {
            String fromMod = entry.getKey();
            for (String toMod : entry.getValue().keySet()) {
                if (!toMod.equals(fromMod)) {
                    ca.merge(toMod, 1, Integer::sum);
                }
            }
        }

        for (String modName : allModuleNames) {
            ca.putIfAbsent(modName, 0);
        }

        // Step 6: Build distributions
        Distribution ceInterDist = new Distribution("Inter-module Ce per module");
        for (String name : allModuleNames) {
            Integer count = ceInter.get(name);
            if (count != null) {
                ceInterDist.addEntry(name, count);
            }
        }

        Distribution caDist = new Distribution("Inter-module Ca per module");
        for (String name : allModuleNames) {
            Integer count = ca.get(name);
            if (count != null) {
                caDist.addEntry(name, count);
            }
        }

        Distribution instabilityDist = new Distribution("Instability (Ce/(Ce+Ca)*100) per module");
        for (String name : allModuleNames) {
            int ce = ceInter.getOrDefault(name, 0);
            int aff = ca.getOrDefault(name, 0);
            int denom = ce + aff;
            int instabilityScaled = denom > 0 ? (ce * 100) / denom : 0;
            instabilityDist.addEntry(name + "=" + instabilityScaled, instabilityScaled);
        }

        // Step 7: Write CSV matrix
        writeDependencyMatrixCsv(dependencyMatrix, allModuleNames);

        logger.info("## GetInterModuleDependencyReport done. {} modules analysed.", allModules.size());

        return List.of(ceInterDist, caDist, instabilityDist);
    }

    @Tool(name = "java_generate_dependency_diagrams",
            value = """
                    Writes the dependency diagrams of a module into the analysis state, where the generated
                    documentation picks them up and prints them.

                    Two levels are written: a package overview, which aggregates the class level reference
                    weights between packages and is emitted whenever the module has a reference graph at all,
                    and class detail per group of the cheapest separation, which also draws the other groups
                    collapsed with the total coupling to them, so the separation is visible and not merely
                    asserted.

                    The source is derived from the class reference graph alone. Nothing in it is model
                    generated, so it is identical on every run for the same module report, and a repeated call
                    replaces the entry of that module rather than appending to it.

                    Every diagram states in its caption how many classes it shows, how many edges were omitted
                    below the configured minimum weight, and how many groups were collapsed. A group above the
                    configured node budget is not drawn and is reported instead; the same holds for a module
                    above the budget even for its package overview. An omission is never silent.
                    """)
    public Map<String, Object> generateDependencyDiagrams(@P("The name of the module to analyse")
                                                          String moduleName) throws IOException {
        logger.info("## GenerateDependencyDiagrams('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return dependencyDiagramNotAvailable("", "No module name given.");
        }

        Module module = getModuleReport(moduleName);

        if (!module.isReportFormatCurrent()) {
            logger.warn("Report of module '{}' carries no weighted reference record", moduleName);
            return dependencyDiagramNotAvailable(moduleName, outdatedReportReason(module));
        }

        return storeDependencyDiagrams(module);
    }

    @Tool(name = "java_generate_all_dependency_diagrams",
            value = """
                    Writes the dependency diagrams of all detected Java modules into the analysis state in one
                    call. Generating a diagram needs no model, so there is no reason to ask for one module at a
                    time.
                    """)
    public Map<String, Object> generateAllDependencyDiagrams() throws IOException {
        List<Map<String, Object>> reports = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (String moduleName : getJavaModuleNames(context.getAnalysisState())) {
            try {
                Module module = getModuleReport(moduleName);

                if (!module.isReportFormatCurrent()) {
                    skipped.add(dependencyDiagramNotAvailable(moduleName, outdatedReportReason(module)));
                    continue;
                }

                reports.add(storeDependencyDiagrams(module));
            } catch (IOException | RuntimeException e) {
                logger.warn("Could not generate the dependency diagrams of '{}': {}", moduleName,
                        e.getMessage());
                skipped.add(dependencyDiagramNotAvailable(moduleName,
                        "Could not generate the dependency diagrams: " + e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", reports);
        result.put("skipped", skipped);

        return result;
    }

    private Map<String, Object> dependencyDiagramNotAvailable(String moduleName, String reasoning) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", moduleName);
        descriptor.put("status", "NOT_AVAILABLE");
        descriptor.put("reasoning", reasoning);

        return descriptor;
    }

    private static final DependencyDiagrams.DiagramSettings DEPENDENCY_DIAGRAM_SETTINGS_FALLBACK =
            DependencyDiagrams.DiagramSettings.DEFAULT;

    /**
     * The diagram budgets come from the workspace configuration, so a run can be reproduced from its
     * {@code config.json}. The built-in defaults apply when a property is absent or unusable.
     */
    private DependencyDiagrams.DiagramSettings dependencyDiagramSettings() {
        return new DependencyDiagrams.DiagramSettings(
                Config.getBudget(context.getProperties(),
                        Config.DIAGRAM_MAX_NODES_PROPERTY, DEPENDENCY_DIAGRAM_SETTINGS_FALLBACK.maxNodes()),
                Config.getBudget(context.getProperties(),
                        Config.DIAGRAM_MAX_OVERVIEW_NODES_PROPERTY,
                        DEPENDENCY_DIAGRAM_SETTINGS_FALLBACK.maxOverviewNodes()),
                Config.getBudget(context.getProperties(),
                        Config.DIAGRAM_MIN_EDGE_WEIGHT_PROPERTY,
                        DEPENDENCY_DIAGRAM_SETTINGS_FALLBACK.minEdgeWeight()),
                Config.getBudget(context.getProperties(),
                        Config.DIAGRAM_MAX_EDGES_PER_NODE_PROPERTY,
                        DEPENDENCY_DIAGRAM_SETTINGS_FALLBACK.maxEdgesPerNode()),
                Config.getBudget(context.getProperties(),
                        Config.DIAGRAM_MAX_GROUP_DIAGRAMS_PROPERTY,
                        DEPENDENCY_DIAGRAM_SETTINGS_FALLBACK.maxGroupDiagrams()));
    }

    /**
     * Writes the dependency diagrams of a module into the analysis state.
     */
    private Map<String, Object> storeDependencyDiagrams(Module module) {
        DependencyDiagrams.DiagramSet diagramSet =
                DependencyDiagrams.of(IntraModuleGraph.of(module), dependencyDiagramSettings());

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
        ObjectNode analysisState = context.getAnalysisState();
        JsonNode existing = analysisState.get(DEPENDENCY_DIAGRAM_PROPERTY);
        ObjectNode diagrams = existing != null && existing.isObject()
                ? (ObjectNode) existing
                : analysisState.putObject(DEPENDENCY_DIAGRAM_PROPERTY);

        ObjectNode moduleDiagrams = mapper.createObjectNode();
        moduleDiagrams.put("moduleName", module.getName());
        moduleDiagrams.put("reasoning", diagramSet.reasoning());
        moduleDiagrams.put("maxNodes", dependencyDiagramSettings().maxNodes());
        moduleDiagrams.put("maxOverviewNodes", dependencyDiagramSettings().maxOverviewNodes());
        moduleDiagrams.put("minEdgeWeight", dependencyDiagramSettings().minEdgeWeight());

        if (diagramSet.overview() == null) {
            moduleDiagrams.putNull("overview");
        } else {
            moduleDiagrams.set("overview", diagramNode(mapper, diagramSet.overview()));
        }

        ArrayNode groupDiagrams = moduleDiagrams.putArray("groupDiagrams");
        for (DependencyDiagrams.Diagram diagram : diagramSet.groups()) {
            groupDiagrams.add(diagramNode(mapper, diagram));
        }

        ArrayNode notDrawn = moduleDiagrams.putArray("notDrawn");
        for (String reason : diagramSet.notDrawn()) {
            notDrawn.add(reason);
        }

        // Replacing the entry of the module keeps a repeated call idempotent instead of appending a second
        // set of diagrams for the same module.
        diagrams.set(module.getName(), moduleDiagrams);

        logger.info("Stored {} diagram(s) for module '{}' in the analysis state",
                1 + diagramSet.groups().size(), module.getName());

        return dependencyDiagramDescriptor(diagramSet);
    }

    private ObjectNode diagramNode(ObjectMapper mapper, DependencyDiagrams.Diagram diagram) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", diagram.name());
        node.put("title", diagram.title());
        node.put("caption", diagram.caption());
        node.put("source", diagram.source());

        return node;
    }

    private Map<String, Object> dependencyDiagramDescriptor(DependencyDiagrams.DiagramSet diagramSet) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", diagramSet.moduleName());
        descriptor.put("status", "OK");
        descriptor.put("overview", diagramSet.overview() == null
                ? null
                : diagramSet.overview().caption());
        descriptor.put("groupDiagramCount", diagramSet.groups().size());
        descriptor.put("groupDiagramCaptions", diagramSet.groups().stream()
                .map(DependencyDiagrams.Diagram::caption)
                .toList());
        descriptor.put("notDrawn", diagramSet.notDrawn());
        descriptor.put("stateProperty", DEPENDENCY_DIAGRAM_PROPERTY + "." + diagramSet.moduleName());
        descriptor.put("reasoning", diagramSet.reasoning()
                + " The source itself is not repeated here: it lives in the analysis state and the"
                + " documentation prints it from there.");

        return descriptor;
    }

    private static final String DEPENDENCY_DIAGRAM_PROPERTY = "dependencyDiagrams";

    @Tool(name = "java_get_split_candidates",
            value = """
                    Proposes where a module could be separated into smaller modules, as a ladder ordered by
                    ascending cost, so the cheapest separation comes first.

                    The cost of a separation is the weight of the reference tie that had to be severed, which is
                    the strongest tie between the two groups that came apart. One strong tie means two sides are
                    not independent, however few ties there are, so a heavy tie cannot hide behind light ones.

                    A separation may cut across package boundaries: the ladder is derived from the class
                    reference graph alone, and each group is described by the package that contributes most of
                    its members together with the members that do not belong to it. The result is a set of
                    candidate separations with their prices, not one asserted answer.

                    The ladder needs no resolution, no group count and no seed, so two calls agree. It is
                    computed over the production classes of the module, because test classes reference
                    everything and would dominate the structure.

                    Alongside it, the exact minimum cut is reported as a secondary figure: the least total
                    coupling that has to be given up to break the module in two. That computation is a cubic
                    pass and is skipped, with the reason, for a module above the configured class limit.
                    """)
    public Map<String, Object> getSplitCandidates(@P("The name of the module to analyse")
                                                  String moduleName) throws IOException {
        logger.info("## GetSplitCandidates('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return splitCandidatesNotAvailable("", "No module name given.");
        }

        Module module = getModuleReport(moduleName);

        if (!module.isReportFormatCurrent()) {
            logger.warn("Report of module '{}' carries no weighted reference record", moduleName);
            return splitCandidatesNotAvailable(moduleName, outdatedReportReason(module));
        }

        return splitCandidateDescriptor(IntraModuleGraph.of(module), true);
    }

    @Tool(name = "java_get_all_split_candidates",
            value = """
                    Returns a compact separation ladder for all detected Java modules.
                    """)
    public Map<String, Object> getAllSplitCandidates() throws IOException {
        List<Map<String, Object>> reports = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (String moduleName : getJavaModuleNames(context.getAnalysisState())) {
            try {
                Module module = getModuleReport(moduleName);

                if (!module.isReportFormatCurrent()) {
                    skipped.add(splitCandidatesNotAvailable(moduleName, outdatedReportReason(module)));
                    continue;
                }

                reports.add(splitCandidateDescriptor(IntraModuleGraph.of(module), false));
            } catch (IOException | RuntimeException e) {
                logger.warn("Could not derive split candidates for '{}': {}", moduleName, e.getMessage());
                skipped.add(splitCandidatesNotAvailable(moduleName,
                        "Could not derive split candidates: " + e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", reports);
        result.put("skipped", skipped);

        return result;
    }

    private Map<String, Object> splitCandidatesNotAvailable(String moduleName, String reasoning) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", moduleName);
        descriptor.put("status", "NOT_AVAILABLE");
        descriptor.put("reasoning", reasoning);

        return descriptor;
    }

    private Map<String, Object> splitCandidateDescriptor(IntraModuleGraph graph, boolean includeMembers) {
        MapWeightedGraph production = graph.productionReferenceGraph();
        List<SeparationLadder.Separation> ladder = SeparationLadder.of(production);
        int returnedCount = Math.min(SPLIT_CANDIDATE_LIMIT, ladder.size());

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", graph.moduleName());
        descriptor.put("status", "OK");
        descriptor.put("nodeCount", production.nodes().size());
        descriptor.put("edgeCount", production.edges().size());
        descriptor.put("separationCount", ladder.size());
        descriptor.put("returnedSeparationCount", returnedCount);
        descriptor.put("separationLimit", SPLIT_CANDIDATE_LIMIT);
        descriptor.put("separations", ladder.subList(0, returnedCount).stream()
                .map(separation -> separationDescriptor(separation, includeMembers))
                .toList());
        descriptor.put("minimumCut", minimumCutDescriptor(production));
        descriptor.put("reasoning", splitReasoning(graph, production, ladder));

        return descriptor;
    }

    private String splitReasoning(IntraModuleGraph graph,
                                  MapWeightedGraph production,
                                  List<SeparationLadder.Separation> ladder) {
        String head = "Separations are derived from " + production.nodes().size()
                + " production class(es) and " + production.edges().size()
                + " reference edge(s) of module '" + graph.moduleName() + "', without a resolution, a group count"
                + " or a seed, ordered by ascending cost over " + ladder.size() + " step(s).";

        if (ladder.isEmpty()) {
            return head + " No step changes the partition, so the production classes are already as separate"
                    + " as the reference graph says they can be.";
        }

        return head + " The cost of a step is the strongest tie between the two groups it separates, so a"
                + " heavy tie appears at its own weight and not before the cheaper steps. A group may span"
                + " several packages; the dominant package and the members outside it are named per group."
                + " The secondary minimum cut figure is the least total coupling that separates the module,"
                + " which can differ from the first ladder step because it may prefer one heavy tie over"
                + " several light ones.";
    }

    private Map<String, Object> separationDescriptor(SeparationLadder.Separation separation,
                                                     boolean includeMembers) {
        List<Set<String>> largestGroups = separation.groups().stream()
                .sorted(Comparator.comparingInt((Set<String> group) -> group.size()).reversed()
                        .thenComparing(group -> group.iterator().next()))
                .toList();
        int listedGroupCount = Math.min(SPLIT_GROUP_LIMIT, largestGroups.size());

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("cost", separation.cost());
        descriptor.put("groupCount", separation.groups().size());
        descriptor.put("listedGroupCount", listedGroupCount);
        descriptor.put("weakestSeveredWeight", separation.weakestSeveredWeight());
        descriptor.put("strongestSeveredWeight", separation.strongestSeveredWeight());
        descriptor.put("totalSeveredWeight", separation.totalSeveredWeight());
        descriptor.put("severedEdges", separation.severedEdges().stream()
                .map(edge -> {
                    Map<String, Object> edgeDescriptor = new LinkedHashMap<>();
                    edgeDescriptor.put("from", edge.from());
                    edgeDescriptor.put("to", edge.to());
                    edgeDescriptor.put("weight", edge.weight());
                    return edgeDescriptor;
                })
                .toList());
        descriptor.put("groups", largestGroups.stream()
                .limit(listedGroupCount)
                .map(group -> groupDescriptor(group, includeMembers))
                .toList());

        return descriptor;
    }

    private Map<String, Object> groupDescriptor(Set<String> group, boolean includeMembers) {
        Map<String, Integer> membersByPackage = new TreeMap<>();

        for (String member : group) {
            membersByPackage.merge(packageOfClass(member), 1, Integer::sum);
        }

        String dominantPackage = "";
        int dominantPackageMemberCount = 0;

        for (Map.Entry<String, Integer> entry : membersByPackage.entrySet()) {
            if (entry.getValue() > dominantPackageMemberCount) {
                dominantPackage = entry.getKey();
                dominantPackageMemberCount = entry.getValue();
            }
        }

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("size", group.size());
        descriptor.put("dominantPackage", dominantPackage);
        descriptor.put("dominantPackageMemberCount", dominantPackageMemberCount);
        descriptor.put("nonConformingMemberCount", group.size() - dominantPackageMemberCount);

        if (includeMembers) {
            descriptor.put("members", List.copyOf(group));
        }

        return descriptor;
    }

    private static String packageOfClass(String className) {
        int lastDot = className.lastIndexOf('.');

        return lastDot < 0 ? "" : className.substring(0, lastDot);
    }

    private Map<String, Object> minimumCutDescriptor(MapWeightedGraph production) {
        Map<String, Object> descriptor = new LinkedHashMap<>();

        if (production.nodes().size() > minimumCutNodeLimit()) {
            descriptor.put("status", "NOT_COMPUTED");
            descriptor.put("reason", "The exact minimum cut is computed in a cubic pass and the module has "
                    + production.nodes().size() + " production classes, above the limit of "
                    + minimumCutNodeLimit() + ". The ladder above is unaffected.");
            return descriptor;
        }

        Optional<MinimumCut.Cut> cut = MinimumCut.of(production);

        if (cut.isEmpty()) {
            descriptor.put("status", "NOT_COMPUTED");
            descriptor.put("reason", "A graph of fewer than two classes cannot be cut in two.");
            return descriptor;
        }

        descriptor.put("status", "OK");
        descriptor.put("weight", cut.get().weight());
        descriptor.put("side", List.copyOf(cut.get().side()));

        return descriptor;
    }

    private static final int SPLIT_CANDIDATE_LIMIT = 5;
    private static final int SPLIT_GROUP_LIMIT = 10;

    private int minimumCutNodeLimit() {
        return Config.getBudget(context.getProperties(), Config.DIAGRAM_MIN_CUT_NODE_LIMIT_PROPERTY,
                MinimumCut.DEFAULT_NODE_LIMIT);
    }

    @Tool(name = "java_get_god_class_ranking",
            value = """
                    Ranks the production classes of a module by how far each stands out from its peers, best
                    candidate first.

                    Every factor is the class's percentile within the module, never an absolute threshold and
                    never a pass or fail verdict, so the result says that a class stands out among its peers
                    and not that it is bad. The factor that drove a class's score is named as well, so the
                    weighting can be disagreed with.

                    The factors are the sum of method cyclomatic complexity, cohesion, accesses to fields
                    declared outside the class, the greatest method nesting depth, the greatest method length
                    and the coupling to other classes of the module.

                    Cohesion is a TCC-like approximation and not TCC: the module report records how often a
                    method touches a field of its own class, not which field, so two methods count as sharing
                    state when both touch any of it.
                    """)
    public Map<String, Object> getGodClassRanking(@P("The name of the module to analyse")
                                                  String moduleName) throws IOException {
        logger.info("## GetGodClassRanking('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return godClassRankingNotAvailable("", "No module name given.");
        }

        Module module = getModuleReport(moduleName);

        if (!module.isReportFormatCurrent()) {
            logger.warn("Report of module '{}' carries no per-method access counters", moduleName);
            return godClassRankingNotAvailable(moduleName, outdatedReportReason(module));
        }

        return godClassRankingDescriptor(module, IntraModuleGraph.of(module), godClassRankingLimit());
    }

    @Tool(name = "java_get_all_god_class_rankings",
            value = """
                    Returns a compact god class ranking for all detected Java modules.
                    """)
    public Map<String, Object> getAllGodClassRankings() throws IOException {
        List<Map<String, Object>> reports = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (String moduleName : getJavaModuleNames(context.getAnalysisState())) {
            try {
                Module module = getModuleReport(moduleName);

                if (!module.isReportFormatCurrent()) {
                    skipped.add(godClassRankingNotAvailable(moduleName, outdatedReportReason(module)));
                    continue;
                }

                reports.add(compactGodClassRankingDescriptor(module));
            } catch (IOException | RuntimeException e) {
                logger.warn("Could not rank the classes of '{}': {}", moduleName, e.getMessage());
                skipped.add(godClassRankingNotAvailable(moduleName,
                        "Could not rank the classes of the module: " + e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", reports);
        result.put("skipped", skipped);

        return result;
    }

    private Map<String, Object> godClassRankingNotAvailable(String moduleName, String reasoning) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", moduleName);
        descriptor.put("status", "NOT_AVAILABLE");
        descriptor.put("reasoning", reasoning);

        return descriptor;
    }

    private Map<String, Object> godClassRankingDescriptor(Module module,
                                                          IntraModuleGraph graph,
                                                          int entryLimit) {
        List<GodClassRanking.RankedClass> ranking = GodClassRanking.of(module, graph, entryLimit);
        int excludedClassCount = GodClassRanking.excludedClassCount(module, graph);

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", module.getName());
        descriptor.put("status", "OK");
        descriptor.put("classCount", ranking.isEmpty() ? 0 : ranking.getFirst().rankedClassCount());
        descriptor.put("excludedClassCount", excludedClassCount);
        descriptor.put("entryLimit", entryLimit);
        descriptor.put("returnedEntryCount", ranking.size());
        descriptor.put("ranking", ranking.stream().map(this::rankedClassDescriptor).toList());
        descriptor.put("reasoning", "Ranked " + descriptor.get("classCount")
                + " production class(es) of the module within the module's project namespace '"
                + graph.projectNamespace() + "'. " + excludedClassCount
                + " class(es) were left out: not production code, generated, or outside the namespace."
                + " No threshold was applied, every factor is a percentile among these classes.");

        return descriptor;
    }

    private Map<String, Object> compactGodClassRankingDescriptor(Module module) {
        IntraModuleGraph graph = IntraModuleGraph.of(module);
        List<GodClassRanking.RankedClass> ranking =
                GodClassRanking.of(module, graph, godClassBatchRankingLimit());

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", module.getName());
        descriptor.put("status", "OK");
        descriptor.put("classCount", ranking.isEmpty() ? 0 : ranking.getFirst().rankedClassCount());
        descriptor.put("excludedClassCount", GodClassRanking.excludedClassCount(module, graph));
        descriptor.put("ranking", ranking.stream().map(this::rankedClassDescriptor).toList());

        return descriptor;
    }

    private int godClassRankingLimit() {
        return Config.getBudget(context.getProperties(), Config.GOD_CLASS_RANKING_LIMIT_PROPERTY,
                GodClassRanking.DEFAULT_RANKING_LIMIT);
    }

    private int godClassBatchRankingLimit() {
        return Config.getBudget(context.getProperties(), Config.GOD_CLASS_BATCH_RANKING_LIMIT_PROPERTY,
                GodClassRanking.DEFAULT_BATCH_RANKING_LIMIT);
    }

    private Map<String, Object> rankedClassDescriptor(GodClassRanking.RankedClass rankedClass) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("name", rankedClass.name());
        descriptor.put("rank", rankedClass.rank());
        descriptor.put("rankedClassCount", rankedClass.rankedClassCount());
        descriptor.put("score", rankedClass.score());
        descriptor.put("dominantFactor", rankedClass.dominantFactor());
        descriptor.put("factors", rankedClass.factors().stream()
                .map(factor -> {
                    Map<String, Object> factorDescriptor = new LinkedHashMap<>();
                    factorDescriptor.put("name", factor.name());
                    factorDescriptor.put("value", Math.round(factor.value() * 100.0) / 100.0);
                    factorDescriptor.put("percentile", factor.percentile());
                    return factorDescriptor;
                })
                .toList());

        return descriptor;
    }

    @Tool(name = "java_get_class_dependency_graph",
            value = """
                    Returns the class reference graph inside a module: one node per top level class of the
                    module and the couplings between them.

                    An edge weight is the API width of the reference, the number of distinct members of the
                    referenced class that are touched, with both directions folded together. The traffic, the
                    number of reference sites, is reported beside it.

                    Only references between two classes of the module count as structure inside the module.
                    A reference to a type the module does not define, and any class outside the module's
                    project namespace, is excluded and the number of exclusions is reported.

                    A structural relation, a superclass, an implemented interface or a declared field type,
                    is listed separately: it is a fact about the type system rather than a measured coupling,
                    so it carries no separation cost.
                    """)
    public Map<String, Object> getClassDependencyGraph(@P("The name of the module to analyse")
                                                       String moduleName) throws IOException {
        logger.info("## GetClassDependencyGraph('{}')", moduleName);

        if (moduleName == null || moduleName.isEmpty()) {
            logger.warn("No module name given");
            return classDependencyGraphNotAvailable("", "No module name given.");
        }

        Module module = getModuleReport(moduleName);

        if (!module.isReportFormatCurrent()) {
            logger.warn("Report of module '{}' carries no weighted reference record", moduleName);
            return classDependencyGraphNotAvailable(moduleName, outdatedReportReason(module));
        }

        return classDependencyGraphDescriptor(IntraModuleGraph.of(module));
    }

    @Tool(name = "java_get_all_class_dependency_graphs",
            value = """
                    Returns a compact class reference graph summary for all detected Java modules.

                    Each entry holds the node and edge counts, how much was excluded, the distribution of
                    edge weights and the heaviest couplings. The full edge list is not returned for every
                    module, because a large project would not fit into a conversation; ask for the graph of
                    a single module when the full list is needed.
                    """)
    public Map<String, Object> getAllClassDependencyGraphs() throws IOException {
        List<Map<String, Object>> reports = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (String moduleName : getJavaModuleNames(context.getAnalysisState())) {
            try {
                Module module = getModuleReport(moduleName);

                if (!module.isReportFormatCurrent()) {
                    skipped.add(classDependencyGraphNotAvailable(moduleName, outdatedReportReason(module)));
                    continue;
                }

                reports.add(compactClassDependencyGraphDescriptor(IntraModuleGraph.of(module)));
            } catch (IOException | RuntimeException e) {
                logger.warn("Could not compute the class dependency graph of '{}': {}", moduleName,
                        e.getMessage());
                skipped.add(classDependencyGraphNotAvailable(moduleName,
                        "Could not compute the class dependency graph: " + e.getMessage()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", reports);
        result.put("skipped", skipped);

        return result;
    }

    private Map<String, Object> classDependencyGraphNotAvailable(String moduleName, String reasoning) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", moduleName);
        descriptor.put("status", "NOT_AVAILABLE");
        descriptor.put("reasoning", reasoning);

        return descriptor;
    }

    private String outdatedReportReason(Module module) {
        String recordedVersion = module.getReportFormatVersion() == 0
                ? "no report format version"
                : "report format version " + module.getReportFormatVersion();

        return "The raw module report records " + recordedVersion
                + " and carries no weighted reference record. Regenerate it as version "
                + Module.CURRENT_REPORT_FORMAT_VERSION
                + " by re-running the Java module report collection.";
    }

    private Map<String, Object> classDependencyGraphDescriptor(IntraModuleGraph graph) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", graph.moduleName());
        descriptor.put("status", "OK");
        descriptor.put("projectNamespace", graph.projectNamespace());
        descriptor.put("nodeCount", graph.nodes().size());
        descriptor.put("nodes", graph.nodes().stream()
                .map(node -> {
                    Map<String, Object> nodeDescriptor = new LinkedHashMap<>();
                    nodeDescriptor.put("name", node.name());
                    nodeDescriptor.put("production", node.production());
                    nodeDescriptor.put("generated", node.generated());
                    return nodeDescriptor;
                })
                .toList());
        descriptor.put("referenceEdgeCount", graph.couplings().size());
        descriptor.put("referenceEdges", graph.couplings().stream()
                .map(coupling -> couplingDescriptor(coupling.from(), coupling.to(),
                        coupling.apiWidth(), coupling.traffic()))
                .toList());
        descriptor.put("structuralEdgeCount", graph.structuralLinks().size());
        descriptor.put("structuralEdges", graph.structuralLinks().stream()
                .map(link -> couplingDescriptor(link.from(), link.to(), 0, 0))
                .toList());
        descriptor.put("excludedNodeCount", graph.excludedNodes());
        descriptor.put("excludedEdgeCount", graph.excludedEdges());
        descriptor.put("reasoning", reasoningFor(graph));

        return descriptor;
    }

    private Map<String, Object> compactClassDependencyGraphDescriptor(IntraModuleGraph graph) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("moduleName", graph.moduleName());
        descriptor.put("status", "OK");
        descriptor.put("projectNamespace", graph.projectNamespace());
        descriptor.put("nodeCount", graph.nodes().size());
        descriptor.put("productionNodeCount", graph.productionReferenceGraph().nodes().size());
        descriptor.put("referenceEdgeCount", graph.couplings().size());
        descriptor.put("structuralEdgeCount", graph.structuralLinks().size());
        descriptor.put("excludedNodeCount", graph.excludedNodes());
        descriptor.put("excludedEdgeCount", graph.excludedEdges());
        descriptor.put("referenceEdgeWidthDistribution", edgeWidthDistribution(graph));
        descriptor.put("strongestCouplings", graph.couplings().stream()
                .sorted(Comparator.comparingInt(IntraModuleGraph.Coupling::apiWidth).reversed()
                        .thenComparing(IntraModuleGraph.Coupling::from)
                        .thenComparing(IntraModuleGraph.Coupling::to))
                .limit(CLASS_GRAPH_STRONGEST_COUPLINGS)
                .map(coupling -> couplingDescriptor(coupling.from(), coupling.to(),
                        coupling.apiWidth(), coupling.traffic()))
                .toList());
        descriptor.put("reasoning", reasoningFor(graph));

        return descriptor;
    }

    private static final int CLASS_GRAPH_STRONGEST_COUPLINGS = 10;

    private Map<String, Object> couplingDescriptor(String from, String to, int apiWidth, int traffic) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("from", from);
        descriptor.put("to", to);
        descriptor.put("apiWidth", apiWidth);
        descriptor.put("traffic", traffic);

        return descriptor;
    }

    private Distribution edgeWidthDistribution(IntraModuleGraph graph) {
        Map<Integer, Integer> edgesByApiWidth = new TreeMap<>();

        for (IntraModuleGraph.Coupling coupling : graph.couplings()) {
            edgesByApiWidth.merge(coupling.apiWidth(), 1, Integer::sum);
        }

        Distribution distribution = new Distribution("Reference edges by API width");
        edgesByApiWidth.forEach((width, count) -> distribution.addEntry(Integer.toString(width), count));

        return distribution;
    }

    private String reasoningFor(IntraModuleGraph graph) {
        return graph.nodes().size() + " class(es), " + graph.couplings().size()
                + " reference edge(s) and " + graph.structuralLinks().size()
                + " structural relation(s) inside the project namespace '"
                + graph.projectNamespace() + "'. " + graph.excludedNodes()
                + " class(es) and " + graph.excludedEdges()
                + " reference(s) were excluded because they belong to no class of the module.";
    }

    private String findTargetModule(String importString, String currentModule,
                                     Map<String, Set<String>> moduleClassNames,
                                     Map<String, Set<String>> modulePackages) {
        // Try exact class match first
        for (Map.Entry<String, Set<String>> entry : moduleClassNames.entrySet()) {
            String moduleName = entry.getKey();
            if (moduleName.equals(currentModule)) continue;
            if (entry.getValue().contains(importString)) {
                return moduleName;
            }
        }

        // Handle wildcard imports: "com.framstag.llmaj.core.*"
        String importPkg;
        if (importString.endsWith(".*")) {
            importPkg = importString.substring(0, importString.length() - 2);
        } else {
            int lastDot = importString.lastIndexOf('.');
            if (lastDot < 0) return null;
            importPkg = importString.substring(0, lastDot);
        }

        if (importPkg.isEmpty()) return null;

        // Longest prefix match to avoid false positives
        String bestMatch = null;
        int bestMatchLength = 0;

        for (Map.Entry<String, Set<String>> entry : modulePackages.entrySet()) {
            String moduleName = entry.getKey();
            if (moduleName.equals(currentModule)) continue;

            for (String pkg : entry.getValue()) {
                if (importPkg.equals(pkg) || importPkg.startsWith(pkg + ".")) {
                    if (pkg.length() > bestMatchLength) {
                        bestMatch = moduleName;
                        bestMatchLength = pkg.length();
                    }
                }
            }
        }

        return bestMatch;
    }

    private void writeDependencyMatrixCsv(Map<String, Map<String, Integer>> matrix, List<String> sortedModules) {
        List<String[]> rows = new LinkedList<>();

        for (String from : sortedModules) {
            Map<String, Integer> deps = matrix.getOrDefault(from, new HashMap<>());
            for (String to : sortedModules) {
                if (from.equals(to)) continue;
                Integer count = deps.get(to);
                if (count != null && count > 0) {
                    rows.add(new String[]{from, to, String.valueOf(count)});
                }
            }
        }

        if (rows.isEmpty()) {
            logger.info("No inter-module dependencies found, skipping CSV");
            return;
        }

        CsvReportWriter.writeCsv(
                context.getWorkingDirectory(),
                "InterModuleDependencyMatrix/InterModuleDependencyMatrix.csv",
                new String[]{"From", "To", "ImportCount"},
                rows
        );

        logger.info("Wrote InterModuleDependencyMatrix/InterModuleDependencyMatrix.csv with {} rows", rows.size());
    }



}
