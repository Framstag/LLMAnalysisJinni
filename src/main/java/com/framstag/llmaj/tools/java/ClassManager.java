package com.framstag.llmaj.tools.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ClassManager {
    private static final Logger logger = LoggerFactory.getLogger(ClassManager.class);

    private final String qualifiedName;
    private String documentation;

    private final List<Annotation> annotations;

    private final Map<String,Integer> countByName = new HashMap<>();

    private final Map<String,Method> methodByName = new HashMap<>();
    private final Map<String,Method> methodByDescriptor = new HashMap<>();

    public ClassManager(String qualifiedName) {
        this.qualifiedName = qualifiedName;
        this.annotations = new LinkedList<>();
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(Annotation annotation) {
        annotations.add(annotation);
    }

    public Method getOrAddMethodForce(String name, String descriptor) {
        Method method = new Method(name,descriptor);
        Integer count = countByName.getOrDefault(name, 0);
        count = count + 1;

        countByName.put(name, count);

        methodByDescriptor.put(descriptor, method);

        if (count > 1) {
            methodByName.remove(name);
        }
        else /* count == 1 */ {
            methodByName.put(name, method);
        }

        return method;
    }

    public Method getOrAddMethodHeuristic(String name, String descriptor) {
        Integer count = countByName.getOrDefault(name, 0);

        if (descriptor != null) {
            return getOrAddMethodForce(name,descriptor);
        }

        if (count == 1) {
            return methodByName.get(name);
        }

        return null;
    }

    public List<Method> getMethods() {
        Set<Method> methods = new HashSet<>();

        methods.addAll(methodByDescriptor.values());
        methods.addAll(methodByName.values());

        return methods.stream().toList();
    }

}
