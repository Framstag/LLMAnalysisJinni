package com.framstag.llmaj.tools.java;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final String name;
    Map<String, PackageManager> packagesByName = new HashMap<>();

    public ModuleManager(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public PackageManager getOrAddPackageByName(String qualifiedName) {
        return packagesByName.computeIfAbsent(qualifiedName, PackageManager::new);
    }

    public List<PackageManager> getPackages() {
        return packagesByName.values().stream().toList();
    }

    public Module getModule() {
        Module module = new Module(name);

        for (PackageManager srcPck : getPackages()) {
            Package pck = new Package(srcPck.getName());

            for (BuildUnitManager buildUnitManager : srcPck.getBuildUnits()) {

                List<Clazz> clazzes = new LinkedList<>();

                for (ClassManager srcClass : buildUnitManager.getClasses()) {
                    List<Method> srcClassMethods = srcClass.getMethods();

                    Clazz clazz = new Clazz(srcClass.getQualifiedName(),
                            srcClass.getDocumentation());

                    for (Annotation annotation : srcClass.getAnnotations()) {
                        clazz.addAnnotation(annotation);
                    }

                    for (Method method : srcClassMethods) {
                        clazz.addMethod(method);
                    }

                    clazzes.add(clazz);
                }

                BuildUnit buildUnit = new BuildUnit(buildUnitManager.getName(),
                        buildUnitManager.isProduction(),
                        buildUnitManager.isGenerated(),
                        buildUnitManager.getImports(),
                        clazzes);

                pck.addBuildUnit(buildUnit);
            }

            module.addPackage(pck);
        }

        return module;
    }
}
