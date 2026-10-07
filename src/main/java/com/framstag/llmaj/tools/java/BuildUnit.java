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
    private final List<ClassReference> references;
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
                     @JsonProperty("references")
                     List<ClassReference> references,
                     @JsonProperty("classes")
                     List<Clazz> classesByName) {
        this.name = name;
        this.production = production;
        this.generated = generated;
        this.imports = imports == null ? List.of() : imports;
        this.references = references == null ? List.of() : references;
        this.clazzes = classesByName;
    }

    /**
     * Builds a build unit without a weighted reference record. Reports written before the record existed are
     * read through the creator above; code that builds a model by hand uses this.
     */
    public BuildUnit(String name,
                     boolean production,
                     boolean generated,
                     List<String> imports,
                     List<Clazz> classesByName) {
        this(name, production, generated, imports, List.of(), classesByName);
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

    /**
     * The weighted reference record. Empty for a report written before the record existed, which is how a
     * consumer detects that a module report has to be regenerated.
     */
    public List<ClassReference> getReferences() {
        return Collections.unmodifiableList(references);
    }

    public List<Clazz> getClazzes() {
        return Collections.unmodifiableList(clazzes);
    }
}
