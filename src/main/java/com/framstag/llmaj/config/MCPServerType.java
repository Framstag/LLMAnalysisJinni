package com.framstag.llmaj.config;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MCPServerType {
    HTTP ("http");

    private final String name;

    private MCPServerType(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }
}
