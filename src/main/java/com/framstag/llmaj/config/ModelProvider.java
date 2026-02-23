package com.framstag.llmaj.config;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ModelProvider {
    OLLAMA ("ollama");

    private final String name;

    private ModelProvider(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }
}
