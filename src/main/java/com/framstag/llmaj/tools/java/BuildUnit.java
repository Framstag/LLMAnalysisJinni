package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class BuildUnit {
    private final String name;
    private final boolean production;
    private final boolean generated;
    private final List<String> imports;
    private final List<Clazz> clazzes;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public BuildUnit(@JsonProperty("name")
                     String name,
                     @JsonProperty("production")
                     boolean production,
                     @JsonProperty("generated")
                     boolean generated,
                     @JsonProperty("imports")
                     List<String> imports,
                     @JsonProperty("classes")
                     List<Clazz> classesByName) {
        this.name = name;
        this.production = production;
        this.generated = generated;
        this.imports = imports;
        this.clazzes = classesByName;
    }

    public String getName() {
        return name;
    }

    public boolean isGenerated() {
        return generated;
    }

    public boolean isProduction() {
        return production;
    }

    public List<String> getImports() {
        return Collections.unmodifiableList(imports);
    }

    public List<Clazz> getClazzes() {
        return Collections.unmodifiableList(clazzes);
    }
}
