package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolService;

import java.nio.file.Path;

public class ChatExecutionContext {
    private final Config config;
    private final ChatModel chatModel;
    private final ToolService toolService;
    private final ToolFilter toolFilter;
    private final ObjectMapper mapper;
    private final String taskId;
    private final Integer loopIndex;
    private final Path workspacePath;

    public ChatExecutionContext(Config config,
                                ChatModel chatModel,
                                ToolService toolService,
                                ToolFilter toolFilter,
                                ObjectMapper mapper,
                                String taskId,
                                Integer loopIndex,
                                Path workspacePath) {
        this.config = config;
        this.chatModel = chatModel;
        this.toolService = toolService;
        this.toolFilter = toolFilter;
        this.mapper = mapper;
        this.taskId = taskId;
        this.loopIndex = loopIndex;
        this.workspacePath = workspacePath;
    }

    public Config getConfig() {
        return config;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public ToolService getToolService() {
        return toolService;
    }

    public ToolFilter getToolFilter() {
        return toolFilter;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public String getTaskId() {
        return taskId;
    }

    public Integer getLoopIndex() {
        return loopIndex;
    }

    public Path getWorkspacePath() {
        return workspacePath;
    }
}
