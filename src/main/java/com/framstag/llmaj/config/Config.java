package com.framstag.llmaj.config;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private Path projectDirectory;
    private Path analysisDirectory;
    private ModelProvider modelProvider;
    private URL modelURL;
    private String modelName;
    private String apiKey;
    private int chatWindowSize;
    private int requestTimeout;
    private int maximumTokens;
    private boolean nativeJSON;
    private boolean logRequests;
    private boolean logResponses;
    private final List<MCPServer> mcpServers;

    public Config() {
        modelProvider = ModelProvider.OLLAMA;
        chatWindowSize = 50;
        requestTimeout = 120;
        maximumTokens = 65536;
        nativeJSON = false;
        logRequests = false;
        logResponses = false;
        mcpServers = new LinkedList<>();
    }

    public void setModelProvider(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @JsonSetter("projectDirectory")
    public void setProjectDirectory(Path projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    @JsonSetter("analysisDirectory")
    public void setAnalysisDirectory(Path analysisDirectory) {
        this.analysisDirectory = analysisDirectory;
    }

    public void setModelURL(URL modelURL) {
        this.modelURL = modelURL;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setChatWindowSize(int chatWindowSize) {
        this.chatWindowSize = chatWindowSize;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public void setMaximumTokens(int maximumTokens) {
        this.maximumTokens = maximumTokens;
    }

    public void setNativeJSON(boolean nativeJSON) {
        this.nativeJSON = nativeJSON;
    }

    public void setLogRequests(boolean logRequests) {
        this.logRequests = logRequests;
    }

    public void setLogResponses(boolean logResponses) {
        this.logResponses = logResponses;
    }

    public ModelProvider getModelProvider() {
        return modelProvider;
    }

    @Override
    public String toString() {
        return "Config{" +
                "projectDirectory=" + projectDirectory +
                ", analysisDirectory=" + analysisDirectory +
                ", modelProvider=" + modelProvider +
                ", modelURL=" + modelURL +
                ", modelName='" + modelName + '\'' +
                ", apiKey='" + "XXX" + '\'' +
                ", chatWindowSize=" + chatWindowSize +
                ", requestTimeout=" + requestTimeout +
                ", maximumTokens=" + maximumTokens +
                ", nativeJSON=" + nativeJSON +
                ", logRequests=" + logRequests +
                ", logResponses=" + logResponses +
                ", mcpServers=" + mcpServers +
                '}';
    }

    public String getApiKey() {
        return apiKey;
    }

    @JsonIgnore
    public Path getProjectDirectory() {
        return projectDirectory;
    }

    @JsonGetter("projectDirectory")
    public String getProjectDirectoryAsString() {
        if (projectDirectory != null) {
            return projectDirectory.toString();
        }

        return null;
    }

    @JsonIgnore
    public Path getAnalysisDirectory() {
        return analysisDirectory;
    }

    @JsonGetter("analysisDirectory")
    public String getAnalysisDirectoryAsString() {
        if (analysisDirectory != null) {
            return analysisDirectory.toString();
        }

        return null;
    }

    public URL getModelURL() {
        return modelURL;
    }

    public String getModelName() {
        return modelName;
    }

    public int getChatWindowSize() {
        return chatWindowSize;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getMaximumTokens() {
        return maximumTokens;
    }

    public boolean isNativeJSON() {
        return nativeJSON;
    }

    public boolean isLogRequests() {
        return logRequests;
    }

    public boolean isLogResponses() {
        return logResponses;
    }

    public List<MCPServer> getMcpServers() {
        return mcpServers;
    }

    public void dumpToLog() {
        logger.info("Model provider:     '{}'", modelProvider);
        logger.info("URL:                '{}'", modelURL.toString());
        logger.info("Model name:         '{}'", modelName);
        logger.info("API key:            {}", apiKey != null ? "<key is set>" : "<key is not set>");
        logger.info("ChatWindow size:    {} ", chatWindowSize);
        logger.info("Timeout:            {} minute(s)", requestTimeout);
        logger.info("Log requests:       {}", logRequests);
        logger.info("Log responses:      {}", logResponses);
        logger.info("Maximum tokens:     {}", maximumTokens);
        logger.info("Native JSON:        {}", nativeJSON);
        logger.info("==");
        logger.info("AnalysisDirectory: '{}'", analysisDirectory);
        logger.info("ProjectDirectory:  '{}'", projectDirectory);

        if (!mcpServers.isEmpty()) {
            for (MCPServer server : mcpServers) {
                logger.info("- MCPServer '{}'", server.getName());
                logger.info("  * type: {}", server.getType());
                logger.info("  * url:  {}", server.getUrl());
            }
        }
    }
}
