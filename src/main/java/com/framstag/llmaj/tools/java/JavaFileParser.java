package com.framstag.llmaj.tools.java;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

import static com.github.javaparser.ast.expr.BinaryExpr.Operator.AND;
import static com.github.javaparser.ast.expr.BinaryExpr.Operator.OR;

public class JavaFileParser {
    private static final Logger logger = LoggerFactory.getLogger(JavaFileParser.class);

    public static void parseJavaFile(Path srcFile,
                                      List<SpecialSubdirectory> specialSubdirectories,
                                      ModuleManager moduleManager,
                                      TypeSolver typeSolver) {
        try {
            logger.info("Parsing java file '{}'...", srcFile);

            CompilationUnit cu = StaticJavaParser.parse(srcFile.toFile());

            SubdirectoryCategory category = ParserHelper.getCategoryOfFile(specialSubdirectories, srcFile, SubdirectoryCategory.OBJ);

            for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type.getFullyQualifiedName().isPresent()) {
                    String packageName = getPackageName(type);
                    String qualifiedName = getClassFileName(type);
                    logger.info("Type: {} - {}", packageName, qualifiedName);

                    PackageManager pck = moduleManager.getOrAddPackageByName(packageName);
                    ClassManager classManager = pck.getOrAddClassByName(qualifiedName);

                    type.getComment().ifPresent(comment -> classManager.setDocumentation(comment.getContent()));

                    ParserHelper.modifyClassAttributesByCategory(classManager, category);

                    classManager.addImports(getCompilationUnitImports(cu, typeSolver));

                    for (AnnotationExpr annotation : type.getAnnotations()) {
                        String annotationName = annotation.getName().asString();
                        String qualifiedAnnotationName = null;

                        try {
                            qualifiedAnnotationName = annotation.resolve().getQualifiedName();
                        } catch (UnsolvedSymbolException e) {
                            // Is expected and ignored
                        }

                        classManager.addAnnotation(new Annotation(annotationName, qualifiedAnnotationName));
                    }

                    List<MethodDeclaration> methods = type.getMethods();

                    for (MethodDeclaration methodDeclaration : methods) {
                        ResolvedMethodDeclaration resolvedMethod = methodDeclaration.resolve();

                        String methodName = resolvedMethod.getName();
                        String methodDescriptor;

                        try {
                            methodDescriptor = resolvedMethod.getName() + resolvedMethod.toDescriptor();
                        } catch (UnsolvedSymbolException e) {
                            logger.debug("Cannot resolve Method API with class '{}' as parameter type", e.getName());
                            methodDescriptor = null;
                        }

                        if (methodDeclaration.isAbstract()) {
                            logger.debug("Skipping abstract method: '{}' / '{}'", methodName, methodDescriptor);
                            continue;
                        }

                        if (methodDeclaration.getBody().isEmpty() ||
                                methodDeclaration.getBody().get().getStatements().isEmpty()) {
                            logger.debug("Skipping method without body: '{}' / '{}'", methodName, methodDescriptor);
                            continue;
                        }

                        logger.debug("Method: {} / {}",
                                methodName,
                                methodDescriptor);

                        Method method = classManager.getOrAddMethodHeuristic(methodName, methodDescriptor);

                        if (method == null) {
                            logger.warn("Method '{}' for class '{}' is overloaded, but we do not have a descriptor for differentiation, skipping...",
                                    methodName,
                                    qualifiedName);

                            continue;
                        }

                        methodDeclaration.getComment().ifPresent(comment -> method.setDocumentation(comment.getContent()));

                        for (AnnotationExpr annotation : methodDeclaration.getAnnotations()) {
                            String annotationName = annotation.getName().asString();
                            String qualifiedAnnotationName = null;

                            try {
                                qualifiedAnnotationName = annotation.resolve().getQualifiedName();
                            } catch (UnsolvedSymbolException e) {
                                // Is expected and ignored
                            }

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

                        Integer cyclomaticComplexity = catchClause.size() +
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
        } catch (Exception e) {
            logger.error("Error during src file parsing", e);
        }
    }

    private static String getPackageName(ClassOrInterfaceDeclaration decl) {
        return decl.findAncestor(CompilationUnit.class)
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(PackageDeclaration::getNameAsString)
                .orElse("");
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
                pkg.ifPresent(packageDeclaration -> name.insert(0, packageDeclaration.getNameAsString() + "."));
                break;
            }
            parent = p.getParentNode();
        }
        return name.toString();
    }

    private static List<String> getCompilationUnitImports(CompilationUnit cu,
                                                          TypeSolver typeSolver) {
        Set<String> imports = new HashSet<>();

        cu.getImports().forEach(importDecl -> {
            if (!importDecl.isAsterisk()) {
                try {
                    ResolvedReferenceTypeDeclaration solved = typeSolver.solveType(importDecl.getName().asString());
                    //ResolvedType type = JavaParserFacade.get(typeSolver).solve(importDecl.getMetaModel().getName());
                    imports.add(solved.getQualifiedName());
                } catch (UnsolvedSymbolException e) {
                    // Errors are expected and will be ignored
                }
            }
        });

        cu.findAll(NameExpr.class).forEach(nameExpr -> {
            try {
                ResolvedValueDeclaration resolved = nameExpr.resolve();
                if (resolved.getType().isReferenceType()) {
                    imports.add(resolved.getType().asReferenceType().getQualifiedName());
                }
            } catch (UnsolvedSymbolException ignored) {
            }
        });

        return new ArrayList<>(imports);
    }
}
