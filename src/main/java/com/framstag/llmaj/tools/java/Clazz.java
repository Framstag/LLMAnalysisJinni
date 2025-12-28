package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Clazz {
    private static final Logger logger = LoggerFactory.getLogger(Clazz.class);

    private final String qualifiedName;

    @JsonIgnore
    private final Set<String> uniqueMethodNames = new HashSet<>();
    @JsonIgnore
    private final Map<String,Integer> countByName = new HashMap<>();

    @JsonIgnore
    private final Map<String,Method> methodByName = new HashMap<>();
    @JsonIgnore
    private final Map<String,Method> methodByDescriptor = new HashMap<>();

    public Clazz(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public String getQualifiedName() {
        return qualifiedName;
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

     @JsonGetter
    public List<Method> Methods() {
        Set<Method> methods = new HashSet<>();

        methods.addAll(methodByDescriptor.values());
        methods.addAll(methodByName.values());

        return methods.stream().toList();
    }

}
