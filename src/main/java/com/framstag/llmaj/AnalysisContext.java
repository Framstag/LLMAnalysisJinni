package com.framstag.llmaj;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.Map;

public class AnalysisContext {
    private final String name;
    private final String version;
    private final Path projectRoot;
    private final Path workingDirectory;
    private final Map<String,String> properties;
    private final ObjectNode analysisState;

    public AnalysisContext(Path projectRoot,
                           Path workingDirectory,
                           Map<String,String> properties,
                           ObjectNode analysisState) {
        this.name = "LLMAnalysisJinni";
        this.version = "1.0.0";
        this.projectRoot = projectRoot;
        this.workingDirectory = workingDirectory;
        this.properties = properties;
        this.analysisState = analysisState;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    public String getProperty(String name) {
        return properties.get(name);
    }

    public Map<String,String> getProperties() {
        return properties;
    }

    public ObjectNode getAnalysisState() {
        return analysisState;
    }
}
