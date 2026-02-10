package com.framstag.llmaj.tools.java;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackageManager {
    private final String name;
    Map<String, ClassManager> classesByName = new HashMap<>();

    public PackageManager(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ClassManager getOrAddClassByName(String name) {
        return classesByName.computeIfAbsent(name, ClassManager::new);
    }

    public List<ClassManager> getClasses() {
        return classesByName.values().stream().toList();
    }
}
