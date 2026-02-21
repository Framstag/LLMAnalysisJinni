package com.framstag.llmaj;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatListener implements ChatModelListener {
    private static final Logger logger = LoggerFactory.getLogger(ChatListener.class);

    private void logSystemMessage(SystemMessage message) {
        logger.info("{} {}","##>",message.text());
    }

    private void logUserMessage(UserMessage message) {
        if (message.hasSingleText()) {
            logger.info("{} {}",">",message.singleText());
        }
        else {
            logger.info("{} {}",">",message);
        }
    }

    private void logAiMessage(AiMessage message) {
        if (message.hasToolExecutionRequests()) {
            for (ToolExecutionRequest request : message.toolExecutionRequests()) {
                logger.info("--> Tool {}: {}",request.name(),request.arguments());
            }
        }

        if (message.text() != null &&
            !message.text().isEmpty()) {
            logger.info("{} {}", "<", message.text());
        }
    }

    private void logToolExecutionResultMessage(ToolExecutionResultMessage message) {
        logger.info("<-- Tool {}: {}",message.toolName(),message.text());
    }

    private void logMessage(ChatMessage message) {
        switch (message) {
            case SystemMessage sm -> logSystemMessage(sm);
            case UserMessage um -> logUserMessage(um);
            case AiMessage am -> logAiMessage(am);
            case ToolExecutionResultMessage tm -> logToolExecutionResultMessage(tm);
            default -> logger.info("{} {}","??? ",message);
        }
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        logger.info("REQUEST {} - {} - {}",
                requestContext.chatRequest().modelName(),
                requestContext.chatRequest().responseFormat() != null ?
                        requestContext.chatRequest().responseFormat().type() : "",
                requestContext.chatRequest().maxOutputTokens() != null ?
                        "max token "+ requestContext.chatRequest().maxOutputTokens() : " ? ");

        if (!requestContext.chatRequest().messages().isEmpty()) {
            logMessage(requestContext.chatRequest().messages().getLast());
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        logger.info("RESPONSE {} - I/O {}/{} - reason {}",
                responseContext.chatResponse().modelName(),
                responseContext.chatResponse().tokenUsage().inputTokenCount(),
                responseContext.chatResponse().tokenUsage().outputTokenCount(),
                responseContext.chatResponse().finishReason());

         if (responseContext.chatResponse().aiMessage() != null) {
            logAiMessage(responseContext.chatResponse().aiMessage());
         }
    }
}
