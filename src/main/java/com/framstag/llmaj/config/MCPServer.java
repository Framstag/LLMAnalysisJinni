package com.framstag.llmaj.config;

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MCPServer {
    private MCPServerType type;
    private String name;
    private URL url;
    private final List<String> command;
    private final Map<String,String> environment;
    private boolean logEvents;

    public MCPServer() {
        this.command = new LinkedList<>();
        this.environment = new HashMap<>();
    }

    public MCPServerType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public URL getUrl() {
        return url;
    }

    public List<String> getCommand() {
        return command;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public boolean isLogEvents() {
        return logEvents;
    }

    @Override
    public String toString() {
        return "MCPServer{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", url=" + url +
                ", command=" + command +
                ", environment=" + environment +
                ", logEvents=" + logEvents +
                '}';
    }
}
