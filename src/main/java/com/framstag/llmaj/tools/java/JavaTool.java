package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.file.FileHelper;
import com.framstag.llmaj.json.ObjectMapperFactory;
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
import com.github.javaparser.resolution.TypeSolver;
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

import java.io.File;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.AccessFlag;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.AND;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.OR;

public class JavaTool {
    private static final Logger logger = LoggerFactory.getLogger(JavaTool.class);

    private final AnalysisContext context;

    public JavaTool(AnalysisContext context) {
        this.context = context;
        logger.info("JavaTool initialized.");

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

    public static String getPackageName(ClassOrInterfaceDeclaration decl) {
        return decl.findAncestor(CompilationUnit.class)
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(PackageDeclaration::getNameAsString)
                .orElse("");
    }
    @Tool(name = "JavaGenerateModuleAnalysisReport",
            value =
                    """
                    Generates a report file containing raw data for further detailed architecture analysis.
                    
                    Returns the id of the report for further usage.
                    """)
    public String generateModuleAnalysisReport(@P("The name of the module")
                                               String moduleName,
                                               @P("The path to the module")
                                               String modulePath) throws IOException {
        logger.info("## JavaGenerateModuleAnalysisReport('{}','{}')",moduleName,modulePath);

        Path rootPath = context.getProjectRoot();
        Path relativePath = Path.of(modulePath);

        Path startPath = rootPath.resolve(relativePath);

        if (!FileHelper.accessAllowed(rootPath, startPath)) {
            logger.error("Not allowed to access '{}' ('{}' '{}')", modulePath,rootPath,relativePath);
            return "";
        }

        Module module = new Module(moduleName);

        List<String> classWildcards = List.of("*.class");
        List<Path> classFiles = new LinkedList<>();

        FileVisitor<Path> classMatcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : classWildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:"+wildcard);
                    if (matcher.matches(file.getFileName())) {
                        classFiles.add(file);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, classMatcherVisitor);

        logger.info("{} *.class file(s) found", classFiles.size());

        for (Path classFile : classFiles) {
            try {
                logger.info("Parsing class file '{}'...", classFile);
                ClassModel classModel = ClassFile.of().parse(classFile);
                String packageName = classModel.thisClass().asSymbol().packageName();
                String qualifiedName = classModel.thisClass().asSymbol().packageName()+"."+
                classModel.thisClass().asSymbol().displayName();

                logger.info("Type: {} - {}",packageName, qualifiedName);

                Package pck = module.getOrAddPackageByName(packageName);
                Clazz clazz = pck.getOrAddClassByName(qualifiedName);

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

                    Method method = clazz.getOrAddMethodForce(methodName,methodDescriptor);
                }
            }
            catch (Exception e) {
                logger.error("Error during class file parsing",e);
            }
        }

        List<String> srcWildcards = List.of("*.java");
        List<Path> srcFiles = new LinkedList<>();

        FileVisitor<Path> srcMatcherVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {

                FileSystem fs = FileSystems.getDefault();

                for (String wildcard : srcWildcards) {
                    PathMatcher matcher = fs.getPathMatcher("glob:"+wildcard);
                    if (matcher.matches(file.getFileName())) {
                        srcFiles.add(file);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(startPath, srcMatcherVisitor);

        TypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(false),
                new JavaParserTypeSolver(new File("/home/tim/projects/spring-petclinic/src/main/java")),
                new JavaParserTypeSolver(new File("/home/tim/projects/spring-petclinic/src/test/java"))
        );

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        StaticJavaParser
                .getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(symbolSolver);

        logger.info("{} *.java file(s) found", classFiles.size());

        for (Path srcFile : srcFiles) {
            try {
                logger.info("Parsing java file '{}'...", srcFile);

                CompilationUnit cu = StaticJavaParser.parse(srcFile.toFile());

                for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    if (type.getFullyQualifiedName().isPresent()) {
                        String packageName = getPackageName(type);
                        String qualifiedName = getClassFileName(type);
                        logger.info("Type: {} - {}",packageName, qualifiedName);

                        Package pck = module.getOrAddPackageByName(packageName);
                        Clazz clazz = pck.getOrAddClassByName(qualifiedName);

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

                            Method method = clazz.getOrAddMethodHeuristic(methodName,methodDescriptor);

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

        logger.info(" === Packages, Classes & Methods");

        for (Package pck : module.getPackages().stream().sorted(Comparator.comparing(Package::getName)).toList()) {
            logger.info("# {}", pck.getName());

            for (Clazz clazz : pck.getClasses().stream().sorted(Comparator.comparing(Clazz::getQualifiedName)).toList()) {
                logger.info("  * {}", clazz.getQualifiedName());

                for (Method method : clazz.Methods().stream().sorted(Comparator.comparing(Method::getName)).toList()) {
                    logger.info("    - {} {}", (method.getDescriptor() != null) ? method.getDescriptor() : method.getName(), method.getCyclomaticComplexity());
                }
            }
        }

        String reportId = "Java_"+moduleName.replace(" ","_");

        logger.info("Id of generated report: {}",reportId);

        ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

        Path reportPath = context.getWorkingDirectory().resolve(reportId+".json");

        logger.info("Writing report file '{}'...",reportPath);
        mapper.writeValue(reportPath.toFile(), module);
        logger.info("Done.");

        return reportId;
    }
}
