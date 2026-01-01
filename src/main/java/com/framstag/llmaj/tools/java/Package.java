package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Package {
    private final String name;
    private final List<Clazz> classes;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Package(@JsonProperty("name")String name) {
        this.name = name;
        this.classes = new LinkedList<>();
    }

    public String getName() {
        return name;
    }

    public void addClass(Clazz clazz) {
        classes.add(clazz);
    }

    public List<Clazz> getClasses() {
        return Collections.unmodifiableList(classes);
    }
}
