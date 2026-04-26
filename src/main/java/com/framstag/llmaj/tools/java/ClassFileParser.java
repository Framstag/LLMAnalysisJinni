package com.framstag.llmaj.tools.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.framstag.llmaj.tools.java.ParserHelper.getCategoryOfFile;
import static java.util.stream.Collectors.toSet;

public class ClassFileParser {
    private static final Logger logger = LoggerFactory.getLogger(ClassFileParser.class);

    private static String getBuildUnitName(String qualifiedName) {
        int dollarPos = qualifiedName.lastIndexOf("$");

        if (dollarPos >= 0) {
            return qualifiedName.substring(0, dollarPos);
        }
        else {
            return qualifiedName;
        }
    }

    public static void parseClassFile(Path classFile,
                                       List<SpecialSubdirectory> specialSubdirectories,
                                       ModuleManager moduleManager) {
        try {
            logger.info("# Parsing class file '{}'...", classFile);
            ClassModel classModel = ClassFile.of().parse(classFile);
            String packageName = classModel.thisClass().asSymbol().packageName();
            String qualifiedName = classModel.thisClass().asSymbol().packageName()+"."+
                    classModel.thisClass().asSymbol().displayName();

            SubdirectoryCategory category = getCategoryOfFile(specialSubdirectories,classFile,SubdirectoryCategory.SRC);

            PackageManager pck = moduleManager.getOrAddPackageByName(packageName);
            BuildUnitManager buildUnit = pck.getOrAddBuildUnitByName(getBuildUnitName(qualifiedName));
            ClassManager classManager = buildUnit.getOrAddClassByName(qualifiedName);

            logger.info("Package: {}",pck.getName());
            logger.info("BuildUnit: {}",buildUnit.getName());
            logger.info("Type: {}",classManager.getQualifiedName());

            ParserHelper.modifyClassAttributesByCategory(classManager,category);

            if (classModel.superclass().isPresent()) {
                String parentQualifiedName = classModel.superclass().get().asSymbol().packageName()+"."+
                        classModel.superclass().get().asSymbol().displayName();

                logger.debug("Parent: {}", parentQualifiedName);
            }

            for (ClassEntry interf : classModel.interfaces()) {
                logger.debug("Implements {}", interf.asInternalName());
            }

            buildUnit.addImports(getClassModelImports(classModel));

            for (MethodModel methodModel : classModel.methods()) {

                String methodName = methodModel.methodName().stringValue();
                String methodDescriptor = methodModel.methodName().stringValue()+ MethodSignature.of(methodModel.methodTypeSymbol()).signatureString();

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

    private static List<String> getClassModelImports(ClassModel classModel) {
        Set<String> imports = new HashSet<>();

        ConstantPool cp = classModel.constantPool();
        for (int i = 1; i < cp.size(); i++) {
            PoolEntry entry = cp.entryByIndex(i);
            if (entry instanceof ClassEntry classEntry) {
                imports.add(classEntry.asSymbol().packageName()+"."+classEntry.asSymbol().displayName());
            }
        }

        Set<String> qualifiedRefs = classModel.methods().stream()
                .flatMap(me -> switch (me) {
                    case MethodModel mm when mm.code().isPresent() -> mm.code().get().elementStream();
                    default -> Stream.empty();
                })
                .filter(e -> e instanceof InvokeInstruction || e instanceof FieldInstruction)
                .map(e -> switch (e) {
                    case InvokeInstruction ii -> ii.owner().asInternalName();  // e.g., "java/util/List"
                    case FieldInstruction fi -> fi.owner().asInternalName();
                    default -> null;
                })
                .filter(Objects::nonNull)
                .map(s -> s.replace('/', '.'))
                .collect(toSet());

        imports.addAll(qualifiedRefs);

        return new ArrayList<String>(imports);
    }

}
