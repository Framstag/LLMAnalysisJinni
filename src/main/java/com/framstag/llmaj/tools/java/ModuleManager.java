package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final String name;
    @JsonIgnore
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

    @JsonGetter("packages")
    public List<PackageManager> getPackages() {
        return packagesByName.values().stream().toList();
    }

    public Module getModule() {
        Module module = new Module(name);

        List<PackageManager> packages = getPackages();

        for (PackageManager srcPck : packages) {
            Package pck = new Package(srcPck.getName());
            List<ClassManager> srcPckClasses = srcPck.getClasses();

            for (ClassManager srcClass : srcPckClasses) {
                List<Method> srcClassMethods = srcClass.getMethods();

                Clazz clazz = new Clazz(srcClass.getQualifiedName(),
                        srcClass.isProduction(),
                        srcClass.isGenerated());

                for (Method method : srcClassMethods) {
                    clazz.addMethod(method);
                }

                pck.addClass(clazz);
            }

            module.addPackage(pck);
        }

        return module;
    }
}
