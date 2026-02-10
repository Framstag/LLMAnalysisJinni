package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Method {
    private final String name;
    private final String descriptor;
    private String documentation;
    private Integer cyclomaticComplexity;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Method(@JsonProperty("name")String name,
                  @JsonProperty("descriptor")String descriptor) {
        this.name = name;
        this.descriptor = descriptor;
        this.cyclomaticComplexity = null;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public Integer getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public void setCyclomaticComplexity(Integer cyclomaticComplexity) {
        this.cyclomaticComplexity = cyclomaticComplexity;
    }
}
