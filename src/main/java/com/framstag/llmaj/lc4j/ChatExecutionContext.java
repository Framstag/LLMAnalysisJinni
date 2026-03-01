package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolService;

public class ChatExecutionContext {
    private final Config config;
    private final ChatModel chatModel;
    private final ToolService toolService;
    private final ObjectMapper mapper;

    public ChatExecutionContext(Config config,
                                ChatModel chatModel,
                                ToolService toolService,
                                ObjectMapper mapper) {
        this.config = config;
        this.chatModel = chatModel;
        this.toolService = toolService;
        this.mapper = mapper;
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

    public ObjectMapper getMapper() {
        return mapper;
    }
}
