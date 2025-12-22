package com.framstag.llmaj;

import java.nio.file.Path;

public class AnalysisContext {
    private final String name;
    private final String version;
    private final Path projectRoot;
    private final Path workingDirectory;

    public AnalysisContext(String name,
                           String version,
                           Path projectRoot,
                           Path workingDirectory) {
        this.name = name;
        this.version = version;
        this.projectRoot = projectRoot;
        this.workingDirectory = workingDirectory;
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
}
