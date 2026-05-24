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

        return List.of(prodParamDist, testParamDist, genParamDist,
                prodLocDist, testLocDist, genLocDist);
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

        return List.of(prodCC, testCC, genCC);
    }
}
