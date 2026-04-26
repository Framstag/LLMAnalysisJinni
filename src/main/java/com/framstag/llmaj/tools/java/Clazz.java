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
    private final String documentation;
    private final List<Annotation> annotations;
    private final List<Method> methods;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Clazz(@JsonProperty("qualifiedName") String qualifiedName,
                 @JsonProperty("documentation") String documentation) {
        this.qualifiedName = qualifiedName;
        this.documentation = documentation;
        annotations = new LinkedList<>();
        methods = new LinkedList<>();
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void addAnnotation(Annotation annotation) {
        annotations.add(annotation);
    }

    public void addMethod(Method method) {
        methods.add(method);
    }

     @JsonGetter
    public List<Method> getMethods() {
        return Collections.unmodifiableList(methods);
    }
}
