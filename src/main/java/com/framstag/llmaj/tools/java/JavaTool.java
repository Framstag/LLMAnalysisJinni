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
                logger.info("  - {}", buildUnit.getName());

                for (String importDesc : buildUnit.getImports().stream().sorted().toList()) {
                    logger.info("    <- {}", importDesc);
                }

                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    logger.info("    * {} production={} generated={}", clazz.getQualifiedName(), clazz.isProduction(), clazz.isGenerated());

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

    @Tool(name = "JavaGenerateModuleAnalysisReport",
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

    @Tool(name = "GetCyclomaticComplexityModuleReport",
            value =
                    """
                            Returns a report regarding the cyclomatic complexity of the module.
                            """)
    public List<Distribution> getCyclomaticComplexityModuleReport(@P("The name of the module")
                                                                  String moduleName) throws IOException {
        logger.info("## GetCyclomaticComplexityModuleReport('{}')", moduleName);

        Module module = getModuleReport(moduleName);

        Map<Integer, Integer> prodDistribution = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (BuildUnit buildUnit : pck.getBuildUnits()) {
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    if (clazz.isProduction() && !clazz.isGenerated()) {
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
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    if (!clazz.isProduction() && !clazz.isGenerated()) {
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
                for (Clazz clazz : buildUnit.getClazzes().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                    if (clazz.isGenerated()) {
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
