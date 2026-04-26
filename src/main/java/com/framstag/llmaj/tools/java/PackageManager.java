package com.framstag.llmaj.tools.java;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackageManager {
    private final String name;
    Map<String, BuildUnitManager> buildUnitsByName = new HashMap<>();

    public PackageManager(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public BuildUnitManager getOrAddBuildUnitByName(String name) {
        return buildUnitsByName.computeIfAbsent(name, BuildUnitManager::new);
    }

    public List<BuildUnitManager> getBuildUnits() {
        return buildUnitsByName.values().stream().toList();
    }
}
