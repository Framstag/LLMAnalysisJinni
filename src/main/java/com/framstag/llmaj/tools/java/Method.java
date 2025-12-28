package com.framstag.llmaj.tools.java;

public class Method {
    private final String name;
    private final String descriptor;
    private Integer cyclomaticComplexity;

    public Method(String name, String signature) {
        this.name = name;
        this.descriptor = signature;
        this.cyclomaticComplexity = null;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public Integer getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public void setCyclomaticComplexity(Integer cyclomaticComplexity) {
        this.cyclomaticComplexity = cyclomaticComplexity;
    }
}
