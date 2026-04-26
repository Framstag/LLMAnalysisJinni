package com.framstag.llmaj.tools.java;

import java.util.Collections;
import java.util.List;

public class BuildUnit {
    private final String name;
    private final List<String> imports;
    private final List<Clazz> clazzes;

    public BuildUnit(String name,
                     List<String> imports,
                     List<Clazz> classesByName) {
        this.name = name;
        this.imports = imports;
        this.clazzes = classesByName;
    }

    public String getName() {
        return name;
    }

    public List<String> getImports() {
        return Collections.unmodifiableList(imports);
    }

    public List<Clazz> getClazzes() {
        return Collections.unmodifiableList(clazzes);
    }
}
