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
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.AND;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.OR;

public class JavaTool {
    private static final Logger logger = LoggerFactory.getLogger(JavaTool.class);

    private final AnalysisContext context;
    private final Map<String, Module> moduleMapCache = new HashMap<>();

    public JavaTool(AnalysisContext context) {
        this.context = context;
        logger.info("JavaTool initialized.");

    }

    private String moduleNameToReportName(String moduleName) {
        return "Java_"+moduleName.replace(" ","_");
    }

    private static SubdirectoryCategory getCategoryOfFile(List<SpecialSubdirectory> specialSubdirectories,
                                                   Path file, SubdirectoryCategory defaultValue) {

        for (SpecialSubdirectory directory : specialSubdirectories) {
            if (file.startsWith(directory.getPath())) {
                return directory.getCategoryId();
            }
        }

        return defaultValue;
    }

    private static void modifyClassAttributesByCategory(ClassManager classManager, SubdirectoryCategory category) {
        switch (category) {
            case SRC, OBJ -> {
                classManager.setProduction(true);
                classManager.setGenerated(false);
            }

            case GEN_SRC -> {
                classManager.setProduction(true);
                classManager.setGenerated(true);
            }
            case TEST_SRC, TEST_OBJ -> {
                classManager.setProduction(false);
                classManager.setGenerated(false);
            }
            case TEST_GEN_SRC -> {
                classManager.setProduction(false);
                classManager.setGenerated(true);
            }
        }
    }

    private JsonNode getModuleWithByName(ObjectNode analysisState, String moduleName) {
        JsonNode modulesSection = analysisState.get("modules");
        JsonNode modulesArray = modulesSection.get("modules");


        if (modulesArray.isArray()) {
            for (JsonNode moduleManager : modulesArray) {
                String currentModuleName = moduleManager.get("name").asText();
                if (currentModuleName.equals(moduleName)) {
                    return moduleManager;
                }
            }
        }

        return null;
    }

    private Path getModulePath(ObjectNode analysisState, String moduleName) {
        JsonNode moduleManager = getModuleWithByName(analysisState, moduleName);

        return Path.of(moduleManager.get("path").asText());
    }

    private List<SpecialSubdirectory> getSpecialSubdirectoriesFromState(String moduleName, Path modulePath) {
        List<SpecialSubdirectory> result = new LinkedList<>();

        ObjectNode analysisState = context.getAnalysisState();

        JsonNode moduleManager = getModuleWithByName(analysisState, moduleName);
        JsonNode subdirectories = moduleManager.get("subdirectories");
        JsonNode directories = subdirectories.get("directories");

        for (JsonNode directory : directories) {
            String path = directory.get("path").asText();
            String categoryId = directory.get("categoryId").asText("");
            String description = directory.get("desc").asText("");

            result.add(new SpecialSubdirectory(modulePath.resolve(path),
                    SubdirectoryCategory.fromString(categoryId),
                    description));
        }

        return result;
    }

    private static String getClassFileName(ClassOrInterfaceDeclaration decl) {
        StringBuilder name = new StringBuilder(decl.getNameAsString());

        Optional<Node> parent = decl.getParentNode();
        while (parent.isPresent()) {
            Node p = parent.get();
            if (p instanceof ClassOrInterfaceDeclaration) {
                name.insert(0, ((ClassOrInterfaceDeclaration) p).getNameAsString() + "$");
            } else if (p instanceof CompilationUnit) {
                // Package anhängen (optional)
                Optional<PackageDeclaration> pkg = ((CompilationUnit) p).getPackageDeclaration();
                if (pkg.isPresent()) {
                    name.insert(0, pkg.get().getNameAsString() + ".");
                }
                break;
            }
            parent = p.getParentNode();
        }
        return name.toString();
    }

    private static String getPackageName(ClassOrInterfaceDeclaration decl) {
        return decl.findAncestor(CompilationUnit.class)
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(PackageDeclaration::getNameAsString)
                .orElse("");
    }

    private static void parseJavaFile(Path srcFile,
                                      List<SpecialSubdirectory> specialSubdirectories,
                                      ModuleManager moduleManager) {
        try {
            logger.info("Parsing java file '{}'...", srcFile);

            CompilationUnit cu = StaticJavaParser.parse(srcFile.toFile());

            SubdirectoryCategory category = getCategoryOfFile(specialSubdirectories,srcFile,SubdirectoryCategory.OBJ);

            for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.getFullyQualifiedName().isPresent()) {
                    String packageName = getPackageName(type);
                    String qualifiedName = getClassFileName(type);
                    logger.info("Type: {} - {}",packageName, qualifiedName);

                    PackageManager pck = moduleManager.getOrAddPackageByName(packageName);
                    ClassManager classManager = pck.getOrAddClassByName(qualifiedName);

                    modifyClassAttributesByCategory(classManager,category);

                    List<MethodDeclaration> methods = type.getMethods();

                    for (MethodDeclaration methodDeclaration : methods) {
                        ResolvedMethodDeclaration resolvedMethod = methodDeclaration.resolve();

                        String methodName = resolvedMethod.getName();
                        String methodDescriptor;

                        try {
                            methodDescriptor = resolvedMethod.getName()+resolvedMethod.toDescriptor();
                        }
                        catch (UnsolvedSymbolException e) {
                            logger.debug("Cannot resolve Method API with class '{}' as parameter type",e.getName());
                            methodDescriptor = null;
                        }

                        if (methodDeclaration.isAbstract()) {
                            logger.debug("Skipping abstract method: '{}' / '{}'",methodName,methodDescriptor);
                            continue;
                        }

                        if (methodDeclaration.getBody().isEmpty() ||
                            methodDeclaration.getBody().get().getStatements().isEmpty()) {
                            logger.debug("Skipping method without body: '{}' / '{}'",methodName,methodDescriptor);
                            continue;
                        }

                        logger.debug("Method: {} / {}",
                                methodName,
                                methodDescriptor);

                        Method method = classManager.getOrAddMethodHeuristic(methodName,methodDescriptor);

                        if (method == null) {
                            logger.warn("Method '{}' for class '{}' is overloaded, but we do not have a descriptor for differentiation, skipping...",
                                    methodName,
                                    qualifiedName);

                            continue;
                        }

                        // All elements that create multiple executions paths
                        List<CatchClause> catchClause = methodDeclaration.findAll(CatchClause.class);
                        List<ConditionalExpr> ternaryExpr = methodDeclaration.findAll(ConditionalExpr.class);
                        List<DoStmt> doStmts = methodDeclaration.findAll(DoStmt.class);
                        List<ForStmt> forStmts = methodDeclaration.findAll(ForStmt.class);
                        List<IfStmt> ifStmts = methodDeclaration.findAll(IfStmt.class);
                        List<SwitchEntry> switchEntrys = methodDeclaration.findAll(SwitchEntry.class).stream().
                                filter(s -> !s.isDefault())
                                .toList();
                        List<WhileStmt> whileStmts = methodDeclaration.findAll(WhileStmt.class);

                        List<BinaryExpr> andExprs = methodDeclaration.findAll(BinaryExpr.class).stream().
                                filter(f -> f.getOperator() == AND).toList();
                        List<BinaryExpr> orExprs = methodDeclaration.findAll(BinaryExpr.class).stream().
                                filter(f -> f.getOperator() == OR).toList();

                        Integer cyclomaticComplexity = catchClause.size()+
                                ternaryExpr.size() +
                                doStmts.size() +
                                forStmts.size() +
                                ifStmts.size() +
                                switchEntrys.size() +
                                whileStmts.size() +
                                andExprs.size() +
                                orExprs.size() +
                                1;

                        method.setCyclomaticComplexity(cyclomaticComplexity);
                    }
                }
            }
        }
        catch (Exception e) {
            logger.error("Error during src file parsing",e);
        }
    }

    private static void dumpModuleToLog(Module module) {
        logger.info(" === Packages, Classes & Methods");

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            logger.info("# {}", pck.getName());

            for (Clazz clazz : pck.getClasses().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                logger.info("  * {} {} {}", clazz.getQualifiedName(),clazz.isProduction(), clazz.isGenerated());

                for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                    logger.info("    - {} {}", (method.getDescriptor() != null) ? method.getDescriptor() : method.getName(), method.getCyclomaticComplexity());
                }
            }
        }
    }

    private static List<Path> getAllMatchingFilesInDirectoryRecursively(List<String> wildcards, Path startPath) throws IOException {
        List<Path> files = new LinkedList<>();

        FileVisitor<Path> srcMatcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : wildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:" + wildcard);
                    if (matcher.matches(file.getFileName())) {
                        files.add(file);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, srcMatcherVisitor);

        return files;
    }

    private static void parseClassFile(Path classFile,
                                       List<SpecialSubdirectory> specialSubdirectories,
                                       ModuleManager moduleManager) {
        try {
            logger.info("Parsing class file '{}'...", classFile);
            ClassModel classModel = ClassFile.of().parse(classFile);
            String packageName = classModel.thisClass().asSymbol().packageName();
            String qualifiedName = classModel.thisClass().asSymbol().packageName()+"."+
            classModel.thisClass().asSymbol().displayName();

            logger.info("Type: {} - {}",packageName, qualifiedName);

            SubdirectoryCategory category = getCategoryOfFile(specialSubdirectories,classFile,SubdirectoryCategory.SRC);

            PackageManager pck = moduleManager.getOrAddPackageByName(packageName);
            ClassManager classManager = pck.getOrAddClassByName(qualifiedName);

            modifyClassAttributesByCategory(classManager,category);

            if (classModel.superclass().isPresent()) {
                String parentQualifiedName = classModel.superclass().get().asSymbol().packageName()+"."+
                        classModel.superclass().get().asSymbol().displayName();

                logger.debug("Parent: {}", parentQualifiedName);
            }

            for (ClassEntry interf : classModel.interfaces()) {
                logger.debug("Implements {}", interf.asInternalName());
            }

            for (MethodModel methodModel : classModel.methods()) {

                String methodName = methodModel.methodName().stringValue();
                String methodDescriptor = methodModel.methodName().stringValue()+MethodSignature.of(methodModel.methodTypeSymbol()).signatureString();

                if (methodModel.flags().has(AccessFlag.BRIDGE)) {
                    logger.debug("Skipping bridge method: '{}'", methodDescriptor);
                    continue;
                }

                if (methodModel.flags().has(AccessFlag.ABSTRACT)) {
                    logger.debug("Skipping abstract method: '{}'", methodDescriptor);
                    continue;
                }

                if (methodModel.flags().has(AccessFlag.SYNTHETIC)) {
                    logger.debug("Skipping synthetic method: '{}'", methodDescriptor);
                    continue;
                }

                if (methodModel.code().isEmpty() || methodModel.code().get().elementList().isEmpty()) {
                    logger.debug("Skipping method without body: '{}'", methodDescriptor);
                    continue;
                }

                logger.debug("Method: {} {}", methodDescriptor, methodModel.flags());

                Method method = classManager.getOrAddMethodForce(methodName,methodDescriptor);
            }
        }
        catch (Exception e) {
            logger.error("Error during class file parsing",e);
        }
    }

    private String storeModuleReport(String moduleName, Module module) throws IOException {
        String reportId = moduleNameToReportName(moduleName);

        logger.info("Id of generated report: {}",reportId);


        Path reportPath = context.getWorkingDirectory().resolve(reportId+".json");

        logger.info("Writing report file '{}'...",reportPath);

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

        Path reportPath = context.getWorkingDirectory().resolve(reportId+".json");

        logger.info("Reading report file '{}'...",reportPath);

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();
        module = mapper.readValue(reportPath.toFile(), Module.class);

        logger.info("Done.");

        moduleMapCache.put(moduleName, module);

        return module;
    }

    @Tool(name = "JavaGenerateModuleAnalysisReport",
            value =
                    """
                    Generates a report file containing raw data for further detailed architecture analysis.
                    
                    Returns the id of the report for further usage.
                    """)
    public String generateModuleAnalysisReport(@P("The name of the moduleManager")
                                               String moduleName) throws IOException {
        logger.info("## JavaGenerateModuleAnalysisReport('{}')",moduleName);

        Path rootPath = context.getProjectRoot();

        Path modulePath = rootPath.resolve(getModulePath(context.getAnalysisState(), moduleName));

        logger.info("Module directory: '{}'", modulePath);

        Path startPath = rootPath.resolve(modulePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", startPath,rootPath,modulePath);
            return "";
        }

        List<SpecialSubdirectory> specialSubdirectories = getSpecialSubdirectoriesFromState(moduleName, modulePath);

        for (SpecialSubdirectory directory : specialSubdirectories) {
            Path specialPath = directory.getPath();

            logger.info("Special directory of type {}: '{}' - {}", directory.getCategoryId(), specialPath, directory.getDescription());

            if (!FileHelper.accessAllowed(rootPath, specialPath)) {
                logger.error("Not allowed to access '{}' ('{}' '{}')", specialPath,rootPath,directory.getPath());
                return "";
            }
        }

        ModuleManager moduleManager = new ModuleManager(moduleName);

        List<Path> classFiles = getAllMatchingFilesInDirectoryRecursively(List.of("*.class"),
                startPath);

        logger.info("{} *.class file(s) found", classFiles.size());

        for (Path classFile : classFiles) {
            parseClassFile(classFile, specialSubdirectories, moduleManager);
        }

        List<Path> srcFiles = getAllMatchingFilesInDirectoryRecursively(List.of("*.java"), startPath);

        logger.info("{} *.java file(s) found", srcFiles.size());

        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(false)
        );

        for (SpecialSubdirectory directory : specialSubdirectories) {
            if (directory.getCategoryId().isSrc()) {
                Path specialPath = rootPath.resolve(directory.getPath());

                logger.info("Adding '{}' as search path to java parser...", specialPath);
                typeSolver.add(new JavaParserTypeSolver(specialPath.toFile()));
            }
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        StaticJavaParser
                .getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(symbolSolver);


        for (Path srcFile : srcFiles) {
            parseJavaFile(srcFile,
                    specialSubdirectories,
                    moduleManager);
        }

        Module module  = moduleManager.getModule();

        dumpModuleToLog(module);

        String reportId = storeModuleReport(moduleName, module);

        return reportId;
    }

    @Tool(name = "GetCyclomaticComplexityModuleReport",
            value =
                    """
                    Returns a report regarding the cyclomatic complexity of the moduleManager.
                    """)
    public List<Distribution> getCyclomaticComplexityModuleReport(@P("The name of the moduleManager")
                                                        String moduleName) throws IOException {
        logger.info("## GetCyclomaticComplexityModuleReport('{}')", moduleName);

        Module module = getModuleReport(moduleName);

        Map<Integer, Integer> productionDistribution = new HashMap<>();

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            for (Clazz clazz : pck.getClasses().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                if (clazz.isProduction()) {
                    for (Method method : clazz.getMethods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                        if (method.getCyclomaticComplexity() != null) {
                            Integer currentCount = productionDistribution.getOrDefault(method.getCyclomaticComplexity(), 0);
                            productionDistribution.put(method.getCyclomaticComplexity(),currentCount+1);

                        }
                    }
                }
            }
        }

        Distribution prodCC = new Distribution("Distribution CyclomaticComplexity Production code");

        for (Integer cc : productionDistribution.keySet().stream().sorted().toList()) {
            prodCC.addEntry(cc.toString(), productionDistribution.get(cc));
        }

        return List.of(prodCC);
    }
}
