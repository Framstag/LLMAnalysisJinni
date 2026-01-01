package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Clazz {
    private static final Logger logger = LoggerFactory.getLogger(Clazz.class);

    private final String qualifiedName;
    private final boolean production;
    private final boolean generated;
    private final List<Method> methods;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Clazz(@JsonProperty("qualifiedName") String qualifiedName,
                 @JsonProperty("production") boolean production,
                 @JsonProperty("generated") boolean generated) {
        this.qualifiedName = qualifiedName;
        this.production = production;
        this.generated = generated;
        methods = new LinkedList<>();
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public boolean isGenerated() {
        return generated;
    }

    public boolean isProduction() {
        return production;
    }

    public void addMethod(Method method) {
        methods.add(method);
    }

     @JsonGetter
    public List<Method> getMethods() {
        return Collections.unmodifiableList(methods);
    }
}
