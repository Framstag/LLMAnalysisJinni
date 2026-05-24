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
    private MethodVisibility visibility;
    private boolean isStatic;
    private boolean isFinal;
    private int parameterCount;
    private Integer linesOfCode;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Method(@JsonProperty("name")
                  String name,
                  @JsonProperty("descriptor")
                  String descriptor) {
        this.name = name;
        this.descriptor = descriptor;
        this.annotations = new LinkedList<>();
        this.cyclomaticComplexity = null;
        this.visibility = null;
        this.isStatic = false;
        this.isFinal = false;
        this.parameterCount = 0;
        this.linesOfCode = null;
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

    public MethodVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(MethodVisibility visibility) {
        this.visibility = visibility;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean isFinal) {
        this.isFinal = isFinal;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public void setParameterCount(int parameterCount) {
        this.parameterCount = parameterCount;
    }

    public Integer getLinesOfCode() {
        return linesOfCode;
    }

    public void setLinesOfCode(Integer linesOfCode) {
        this.linesOfCode = linesOfCode;
    }
}
