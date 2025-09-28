package com.framstag.llmaj;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolService;

public class ChatExecutionContext {
    private ChatModel chatModel;
    private ChatMemory memory;
    private ToolService toolService;
    private ServiceOutputParser outputParser;

    public ChatExecutionContext(ChatModel chatModel,
                                ChatMemory memory,
                                ToolService toolService,
                                ServiceOutputParser outputParser) {
        this.chatModel = chatModel;
        this.memory = memory;
        this.toolService = toolService;
        this.outputParser = outputParser;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

        public ChatMemory getMemory() {
        return memory;
    }

    public ToolService getToolService() {
        return toolService;
    }

    public ServiceOutputParser getOutputParser() {
        return outputParser;
    }
}
