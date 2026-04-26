package com.framstag.llmaj.tools.java;

import java.util.*;

public class BuildUnitManager {
    private final String name;
    private final Set<String> imports;
    private final Map<String, ClassManager> classesByName;

    public BuildUnitManager(String name) {
        this.name = name;
        this.imports = new HashSet<>();
        this.classesByName = new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public List<String> getImports() {
        return new ArrayList<>(imports);
    }

    public void addImports(Collection<String> imports) {
        this.imports.addAll(imports);
    }

    public ClassManager getOrAddClassByName(String name) {
        return classesByName.computeIfAbsent(name, ClassManager::new);
    }

    public List<ClassManager> getClasses() {
        return classesByName.values().stream().toList();
    }

}
