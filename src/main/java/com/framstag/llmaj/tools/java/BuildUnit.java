package com.framstag.llmaj.tools.java;

import java.util.Collections;
import java.util.List;

public class BuildUnit {
    private final String name;
    private final boolean production;
    private final boolean generated;
    private final List<String> imports;
    private final List<Clazz> clazzes;

    public BuildUnit(String name,
                     boolean production,
                     boolean generated,
                     List<String> imports,
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
