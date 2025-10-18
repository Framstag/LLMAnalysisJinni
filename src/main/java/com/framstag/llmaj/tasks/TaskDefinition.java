package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TaskDefinition {
    private String id;
    private boolean active = true;
    private String name;
    private Path systemPrompt;
    private Path prompt;
    private Path responseFormat;
    private String responseProperty;
    private Set<String> tags = new HashSet<>();
    private Set<String> dependsOn = new HashSet<>();
    private String loopOn;

    public String getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public String getName() {
        return name;
    }

    public Path getSystemPrompt() {
        return systemPrompt;
    }

    public Path getPrompt() {
        return prompt;
    }

    public Path getResponseFormat() {
        return responseFormat;
    }

    public String getResponseProperty() {
        return responseProperty;
    }

    public Set<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public Set<String> getDependsOn() {
        return dependsOn;
    }

    public boolean hasLoopOn() {
        return loopOn != null && !loopOn.isEmpty();
    }

    public String getLoopOn() {
        return loopOn;
    }

    public static List<TaskDefinition> loadTasks(Path path) throws IOException {
        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yamlFactory);
        mapper.findAndRegisterModules();

        YAMLParser parser = yamlFactory.createParser(path.toFile());
        return mapper.readValues(parser,new TypeReference<TaskDefinition>(){}).readAll();
    }

    @Override
    public String toString() {
        return "TaskDefinition{" +
                "id='" + id + '\'' +
                ", active=" + active +
                ", name='" + name + '\'' +
                ", systemPrompt=" + systemPrompt +
                ", prompt=" + prompt +
                ", responseFormat=" + responseFormat +
                ", responseProperty='" + responseProperty + '\'' +
                ", tags=" + tags +
                ", dependsOn=" + dependsOn +
                ", loopOn='" + loopOn + '\'' +
                '}';
    }
}
