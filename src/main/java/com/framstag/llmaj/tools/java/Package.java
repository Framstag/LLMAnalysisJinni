package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Package {
    private final String name;
    @JsonIgnore
    Map<String,Clazz> classesByName = new HashMap<>();

    public Package(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Clazz getOrAddClassByName(String name) {
        return classesByName.computeIfAbsent(name, Clazz::new);
    }

    @JsonGetter("classes")
    public List<Clazz> getClasses() {
        return classesByName.values().stream().toList();
    }
}
