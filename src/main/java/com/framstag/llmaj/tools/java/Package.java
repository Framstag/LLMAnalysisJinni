package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Package {
    private final String name;
    private final List<BuildUnit> buildUnits;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Package(@JsonProperty("name")String name) {
        this.name = name;
        this.buildUnits = new LinkedList<>();
    }

    public String getName() {
        return name;
    }

    public void addBuildUnit(BuildUnit buildUnit) {
        buildUnits.add(buildUnit);
    }

    public List<BuildUnit> getBuildUnits() {
        return Collections.unmodifiableList(buildUnits);
    }
}
