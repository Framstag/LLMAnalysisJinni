package com.framstag.llmaj.config;

import java.net.URL;

public class MCPServer {
    private MCPServerType type;
    private String name;
    private URL url;

    public MCPServerType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public URL getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "MCPServer{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", url=" + url +
                '}';
    }
}
