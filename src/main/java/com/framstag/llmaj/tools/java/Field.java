package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Field {
    private final String name;
    private final String type;
    private final MethodVisibility visibility;
    private final boolean isStatic;
    private final boolean isFinal;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Field(@JsonProperty("name") String name,
                 @JsonProperty("type") String type,
                 @JsonProperty("visibility") MethodVisibility visibility,
                 @JsonProperty("isStatic") boolean isStatic,
                 @JsonProperty("isFinal") boolean isFinal) {
        this.name = name;
        this.type = type;
        this.visibility = visibility;
        this.isStatic = isStatic;
        this.isFinal = isFinal;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public MethodVisibility getVisibility() {
        return visibility;
    }

    @JsonProperty("isStatic")
    public boolean isStatic() {
        return isStatic;
    }

    @JsonProperty("isFinal")
    public boolean isFinal() {
        return isFinal;
    }
}