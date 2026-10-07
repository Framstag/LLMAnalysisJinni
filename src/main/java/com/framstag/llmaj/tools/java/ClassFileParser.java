package com.framstag.llmaj.tools.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.FieldModel;
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

    /**
     * The top level class that encloses a qualified name. A nested class belongs to the build unit of its
     * enclosing class, which is also the granularity of the reference record.
     */
    public static String getBuildUnitName(String qualifiedName) {
        int dollarPos = qualifiedName.lastIndexOf("$");

        if (dollarPos >= 0) {
            return qualifiedName.substring(0, dollarPos);
        }
        else {
            return qualifiedName;
        }
    }

    /**
     * A reference is internal when its owner belongs to the same build unit, which is the top level class
     * that encloses the referencing class. Only foreign references become edges of the class reference
     * graph.
     */
    private static boolean isInternal(String qualifiedName, String buildUnitName) {
        return getBuildUnitName(qualifiedName).equals(buildUnitName);
    }

    /**
     * Records a superclass, an interface or a declared field type as a structural reference. It carries no
     * reference site, so it adds nothing to a separation cost, but it is never dropped from a diagram.
     */
    private static void addStructuralReference(BuildUnitManager buildUnit, String targetQualifiedName) {
        if (isInternal(targetQualifiedName, buildUnit.getName())) {
            return;
        }

        buildUnit.addReference(targetQualifiedName, null, true);
    }

    /**
     * Turns a field descriptor into a qualified class name, or {@code null} for a primitive. Array types
     * resolve to their element type, which is the class the reference points at.
     */
    private static String descriptorToQualifiedName(String descriptor) {
        if (descriptor == null) {
            return null;
        }

        String elementDescriptor = descriptor;

        while (elementDescriptor.startsWith("[")) {
            elementDescriptor = elementDescriptor.substring(1);
        }

        if (elementDescriptor.startsWith("L") && elementDescriptor.endsWith(";")) {
            return elementDescriptor.substring(1, elementDescriptor.length() - 1).replace('/', '.');
        }

        return null;
    }

    /**
     * Counts the field accesses and calls of one method and records the foreign ones as reference sites.
     * A call to the enclosing build unit is internal and never crosses a seam, so it is counted but not
     * turned into an edge.
     */
    private static void countAccesses(MethodModel methodModel,
                                      Method method,
                                      BuildUnitManager buildUnit,
                                      String superClassName) {
        int internalFieldAccesses = 0;
        int foreignFieldAccesses = 0;
        int internalCalls = 0;
        int foreignCalls = 0;

        for (var element : methodModel.code().get().elementList()) {
            if (element instanceof FieldInstruction fieldInstruction) {
                String ownerQualifiedName = fieldInstruction.owner().asInternalName().replace('/', '.');

                if (isInternal(ownerQualifiedName, buildUnit.getName())) {
                    internalFieldAccesses++;
                } else {
                    foreignFieldAccesses++;
                    buildUnit.addReference(ownerQualifiedName,
                            fieldInstruction.owner().asInternalName() + "#" + fieldInstruction.name().stringValue(),
                            false);
                }
            } else if (element instanceof InvokeInstruction invokeInstruction) {
                String ownerQualifiedName = invokeInstruction.owner().asInternalName().replace('/', '.');

                if (isInternal(ownerQualifiedName, buildUnit.getName())) {
                    internalCalls++;
                } else if (isSuperConstructorCall(invokeInstruction, ownerQualifiedName, superClassName)) {
                    // The compiler emits a call to the superclass constructor even when the source declares
                    // none. It is implied by the inheritance that the structural edge already records, and
                    // counting it would give every subclass a coupling to its parent that nobody can act on.
                    internalCalls++;
                } else {
                    foreignCalls++;
                    buildUnit.addReference(ownerQualifiedName,
                            invokeInstruction.owner().asInternalName() + "#"
                                    + invokeInstruction.name().stringValue()
                                    + invokeInstruction.type().stringValue(),
                            false);
                }
            }
        }

        method.setInternalFieldAccesses(internalFieldAccesses);
        method.setForeignFieldAccesses(foreignFieldAccesses);
        method.setInternalCalls(internalCalls);
        method.setForeignCalls(foreignCalls);
    }

    private static boolean isSuperConstructorCall(InvokeInstruction invokeInstruction,
                                                   String ownerQualifiedName,
                                                   String superClassName) {
        return superClassName != null
                && superClassName.equals(ownerQualifiedName)
                && "<init>".equals(invokeInstruction.name().stringValue());
    }

    /**
     * Parses one class file into the module manager.
     *
     * @return true when the class file contributed a type to the module, false when it could not be read,
     * so the caller can count the files a module did not analyse
     */
    public static boolean parseClassFile(Path classFile,
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

            ParserHelper.modifyClassAttributesByCategory(buildUnit,category);

            String superClassName = null;

            if (classModel.superclass().isPresent()) {
                String parentQualifiedName = classModel.superclass().get().asSymbol().packageName()+"."+
                        classModel.superclass().get().asSymbol().displayName();

                logger.debug("Parent: {}", parentQualifiedName);
                classManager.setSuperClass(parentQualifiedName);
                superClassName = parentQualifiedName;
                addStructuralReference(buildUnit, parentQualifiedName);
            }

            for (ClassEntry interf : classModel.interfaces()) {
                String ifaceQualifiedName = interf.asSymbol().packageName()+"."+interf.asSymbol().displayName();
                logger.debug("Implements {}", ifaceQualifiedName);
                classManager.addInterface(ifaceQualifiedName);
                addStructuralReference(buildUnit, ifaceQualifiedName);
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

                // Extract method visibility from access flags
                if (methodModel.flags().has(AccessFlag.PUBLIC)) {
                    method.setVisibility(MethodVisibility.PUBLIC);
                } else if (methodModel.flags().has(AccessFlag.PROTECTED)) {
                    method.setVisibility(MethodVisibility.PROTECTED);
                } else if (methodModel.flags().has(AccessFlag.PRIVATE)) {
                    method.setVisibility(MethodVisibility.PRIVATE);
                } else {
                    method.setVisibility(MethodVisibility.PACKAGE_PRIVATE);
                }

                // Set parameter count from method type descriptor
                var methodType = methodModel.methodTypeSymbol();
                method.setParameterCount(methodType.parameterCount());

                method.setStatic(methodModel.flags().has(AccessFlag.STATIC));
                method.setFinal(methodModel.flags().has(AccessFlag.FINAL));

                countAccesses(methodModel, method, buildUnit, superClassName);
            }

            // Extract fields from class bytecode
            for (FieldModel fieldModel : classModel.fields()) {
                String fieldName = fieldModel.fieldName().stringValue();
                String fieldType = fieldModel.fieldTypeSymbol().descriptorString();

                MethodVisibility vis;
                if (fieldModel.flags().has(AccessFlag.PUBLIC)) {
                    vis = MethodVisibility.PUBLIC;
                } else if (fieldModel.flags().has(AccessFlag.PROTECTED)) {
                    vis = MethodVisibility.PROTECTED;
                } else if (fieldModel.flags().has(AccessFlag.PRIVATE)) {
                    vis = MethodVisibility.PRIVATE;
                } else {
                    vis = MethodVisibility.PACKAGE_PRIVATE;
                }

                boolean isStatic = fieldModel.flags().has(AccessFlag.STATIC);
                boolean isFinal = fieldModel.flags().has(AccessFlag.FINAL);

                classManager.addField(new Field(fieldName, fieldType, vis, isStatic, isFinal));

                // A declared field type is a structural relation, not a measured coupling: it is drawn
                // regardless of any diagram cutoff and carries no reference site of its own.
                String fieldTypeQualifiedName = descriptorToQualifiedName(fieldType);

                if (fieldTypeQualifiedName != null) {
                    addStructuralReference(buildUnit, fieldTypeQualifiedName);
                }
            }
        }
        
        catch (Exception e) {
            // The class file is left out of the module report; the caller reports the count per module
            // and the detail stays at DEBUG.
            logger.debug("Cannot parse the class file '{}'", classFile, e);

            return false;
        }

        return true;
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
