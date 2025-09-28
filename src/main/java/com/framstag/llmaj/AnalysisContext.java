package com.framstag.llmaj;

public class AnalysisContext {
    private final String name;
    private final String version;
    private final String projectRoot;

    public AnalysisContext(String name, String version, String projectRoot) {
        this.name = name;
        this.version = version;
        this.projectRoot = projectRoot;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getProjectRoot() {
        return projectRoot;
    }
}
