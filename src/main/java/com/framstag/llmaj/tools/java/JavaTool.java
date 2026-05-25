package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.file.FileHelper;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.common.Distribution;
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

    public JavaTool(AnalysisContext context) {
        this.context = context;
        logger.info("JavaTool initialized.");
    }

    private String moduleNameToReportName(String moduleName) {
        return "Java_" + moduleName.replace(" ", "_");
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

        Path reportPath = context.getWorkingDirectory().resolve(reportId + ".json");

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

        Path reportPath = context.getWorkingDirectory().resolve(reportId + ".json");

        logger.info("Reading report file '{}'...", reportPath);

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
        module = mapper.readValue(reportPath.toFile(), Module.class);

        logger.info("Done.");

        moduleMapCache.put(moduleName, module);

        return module;
    }

    private TypeSolver createTypeResolver(Path rootPath,
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
                                System.err.println("Could not load JAR: " + jarPath + " — " + e.getMessage());
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

        List<Path> classFiles = FileHelper.getAllMatchingFilesInDirectoryRecursively(List.of("*.class"),
                startPath);

        logger.info("{} *.class file(s) found", classFiles.size());

        for (Path classFile : classFiles) {
            ClassFileParser.parseClassFile(classFile, specialSubdirectories, moduleManager);
        }

        List<Path> srcFiles = FileHelper.getAllMatchingFilesInDirectoryRecursively(List.of("*.java"), startPath);

        logger.info("{} *.java file(s) found", srcFiles.size());

        TypeSolver typeSolver = createTypeResolver(rootPath, context.getProperties(), specialSubdirectories);

        StaticJavaParser
                .getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));


        for (Path srcFile : srcFiles) {
            JavaFileParser.parseJavaFile(srcFile,
                    specialSubdirectories,
                    moduleManager,
                    typeSolver);
        }

        Module module = moduleManager.getModule();

        dumpModuleToLog(module);

        return storeModuleReport(moduleName, module);
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

        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "VisibilityDistribution.csv",
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

                CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "InheritanceDepth.csv",
                new String[]{"production","test","generated"},
                prodDepth, testDepth, genDepth);
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "InterfaceCount.csv",
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

                CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "MethodParamCount.csv",
                new String[]{"production","test","generated"},
                prodParamCount, testParamCount, genParamCount);
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "MethodLinesOfCode.csv",
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

        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "NestingDepth.csv",
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

                CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "CyclomaticComplexity.csv",
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

        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "FieldVisibility.csv",
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

                CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "FieldCountPerClass.csv",
                new String[]{"production","test","generated"},
                prodFieldCount, testFieldCount, genFieldCount);
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "FieldToMethodRatio.csv",
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

                CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "ClassCoupling.csv",
                new String[]{"production","test","generated"},
                prodCc, testCc, genCc);
        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "ModuleDependencies.csv",
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
        CsvReportWriter.writeMapCsv(context.getWorkingDirectory(), "TestCoverage.csv", tcData);
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
        CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "CircularDeps.csv", "CycleInfo", result);
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
        CsvReportWriter.writeMultiIntMapCsv(context.getWorkingDirectory(), "MethodCount.csv",
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
        CsvReportWriter.writeMapCsv(context.getWorkingDirectory(), "DocumentationRatio.csv", docData);
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
                CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "DataClasses.csv", "ClassName", result);
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
                CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "BooleanParamFlags.csv", "Method", result);
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

        CsvReportWriter.writeMultiMapCsv(context.getWorkingDirectory(), "AnnotationDensity.csv",
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

        CsvReportWriter.writeListCsv(context.getWorkingDirectory(), "PackageTangles.csv", "PackageCycleInfo", result);
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

        CsvReportWriter.writeMapCsv(context.getWorkingDirectory(), "ImportDiversity.csv", allImports);

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


}
