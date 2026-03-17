package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

public class Method {
    private final String name;
    private final String descriptor;
    private String documentation;
    private final List<Annotation> annotations;
    private Integer cyclomaticComplexity;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Method(@JsonProperty("name")String name,
                  @JsonProperty("descriptor")String descriptor) {
        this.name = name;
        this.descriptor = descriptor;
        this.annotations = new LinkedList<>();
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

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(Annotation annotation) {
        annotations.add(annotation);
    }

    public Integer getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public void setCyclomaticComplexity(Integer cyclomaticComplexity) {
        this.cyclomaticComplexity = cyclomaticComplexity;
    }
}
