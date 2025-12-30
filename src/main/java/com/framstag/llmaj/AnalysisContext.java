package com.framstag.llmaj;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;

public class AnalysisContext {
    private final String name;
    private final String version;
    private final Path projectRoot;
    private final Path workingDirectory;
    private final ObjectNode analysisState;

    public AnalysisContext(Path projectRoot,
                           Path workingDirectory,
                           ObjectNode analysisState) {
        this.name = "LLMAnalysisJinni";
        this.version = "1.0.0";
        this.projectRoot = projectRoot;
        this.workingDirectory = workingDirectory;
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

    public ObjectNode getAnalysisState() {
        return analysisState;
    }
}
