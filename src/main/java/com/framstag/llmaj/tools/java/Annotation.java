package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Annotation {
    private final String name;
    private final String qualifiedName;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Annotation(@JsonProperty("name") String name,
                      @JsonProperty("qualifiedName") String qualifiedName) {
        this.name = name;
        this.qualifiedName = qualifiedName;
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }
}
