package com.framstag.llmaj.tools.java;

import java.nio.file.Path;

public class SpecialSubdirectory {
    private final Path path;
    private final SubdirectoryCategory CategoryId;
    private final String description;

    public SpecialSubdirectory(Path path, SubdirectoryCategory categoryId, String description) {
        this.path = path;
        CategoryId = categoryId;
        this.description = description;
    }

    public Path getPath() {
        return path;
    }

    public SubdirectoryCategory getCategoryId() {
        return CategoryId;
    }

    public String getDescription() {
        return description;
    }
}
