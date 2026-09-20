package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolService;

import java.nio.file.Path;

import com.framstag.llmaj.display.ProgressCallback;

public class ChatExecutionContext {
    private final Config config;
    private final ChatModel chatModel;
    private final ToolService toolService;
    private final ToolFilter toolFilter;
    private final ObjectMapper mapper;
    private final String taskId;
    private final Integer loopIndex;
    private final Path workspacePath;
    private ProgressCallback progressCallback;

    /**
     * Which attempt of this step is running, starting at 1. Used for the chat log file name.
     */
    private int attemptNumber = 1;

    /**
     * The text appended to the user message of a further attempt, or null for the first one.
     */
    private String repairHint;

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

    public ProgressCallback getProgressCallback() {
        if (progressCallback == null) {
            return ProgressCallback.noOp();
        }
        return progressCallback;
    }

    public void setProgressCallback(ProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getRepairHint() {
        return repairHint;
    }

    public void setRepairHint(String repairHint) {
        this.repairHint = repairHint;
    }
}
