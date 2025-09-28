package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class TaskDefinition {
    private String id;
    private boolean active;
    private String name;
    private Path systemPrompt;
    private Path prompt;
    private Path responseFormat;
    private String responseProperty;

    public TaskDefinition() {
        active = true;
    }

    public TaskDefinition(String id,
                          boolean active,
                          String name,
                          Path systemPrompt,
                          Path prompt,
                          Path responseFormat,
                          String responseProperty) {
        this.id = id;
        this.active = active;
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.prompt = prompt;
        this.responseFormat = responseFormat;
        this.responseProperty = responseProperty;
    }

    public String getId() {
        return id;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public static List<TaskDefinition> loadTasks(Path path) throws IOException {

        YAMLFactory yamlFactory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(yamlFactory);

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
                '}';
    }
}
