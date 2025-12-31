package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Module {
    private final String name;
    private final List<Package> packages;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Module(@JsonProperty("name")String name) {
        this.name = name;
        this.packages = new LinkedList<>();
    }

    public String getName() {
        return name;
    }

    public void addPackage(Package pck) {
        this.packages.add(pck);
    }

    public List<Package> getPackages() {
        return Collections.unmodifiableList(packages);
    }
}
