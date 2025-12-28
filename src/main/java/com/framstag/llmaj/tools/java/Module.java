package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Module {
    private final String name;
    @JsonIgnore
    Map<String,Package> packagesByName = new HashMap<>();

    public Module(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Package getOrAddPackageByName(String qualifiedName) {
        return packagesByName.computeIfAbsent(qualifiedName, Package::new);
    }

    @JsonGetter("packages")
    public List<Package> getPackages() {
        return packagesByName.values().stream().toList();
    }
}
