package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.json.JsonHelper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class ChatExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ChatExecutor.class);

    private static final String ANSWER_ONLY_WITH_THE_FOLLOWING_JSON = "\nYou must answer strictly in the following JSON format: ";


    private static final ToolArgumentsErrorHandler DEFAULT_TOOL_ARGUMENTS_ERROR_HANDLER = (error, context) -> {
        if (error instanceof RuntimeException re) {
            throw re;
        } else {
            throw new RuntimeException(error);
        }
    };
    private static final ToolExecutionErrorHandler DEFAULT_TOOL_EXECUTION_ERROR_HANDLER = (error, context) -> {
        String errorMessage = Utils.isNullOrBlank(error.getMessage()) ? error.getClass().getName() : error.getMessage();
        return ToolErrorHandlerResult.text(errorMessage);
    };

    private final int maxSequentialToolsInvocations = 100;
    private final ExecutorService executor;
    private final ToolArgumentsErrorHandler argumentsErrorHandler;
    private final ToolExecutionErrorHandler executionErrorHandler;
    private final Function<ToolExecutionRequest, ToolExecutionResultMessage> toolHallucinationStrategy;

    public ChatExecutor() {
        executor =  DefaultExecutorProvider.getDefaultExecutorService();
        toolHallucinationStrategy = HallucinatedToolNameStrategy.THROW_EXCEPTION;
        argumentsErrorHandler = DEFAULT_TOOL_ARGUMENTS_ERROR_HANDLER;
        executionErrorHandler = DEFAULT_TOOL_EXECUTION_ERROR_HANDLER;
    }

    private ToolExecutionResult applyToolHallucinationStrategy(ToolExecutionRequest toolRequest) {
        ToolExecutionResultMessage toolResultMessage = this.toolHallucinationStrategy.apply(toolRequest);
        return ToolExecutionResult.builder().resultText(toolResultMessage.text()).build();
    }

    private String cleanupToolName(String toolName) {
        int i = toolName.indexOf("<");

        if (i >=0) {
            String correctedToolName = toolName.substring(0,i);

            logger.warn("Corrected tool name from '{}' to '{}'",toolName,correctedToolName);

            return correctedToolName;
        }

        return toolName;
    }

    private static ToolExecutionResult executeWithErrorHandling(ToolExecutionRequest toolRequest, ToolExecutor toolExecutor, InvocationContext invocationContext, ToolArgumentsErrorHandler argumentsErrorHandler, ToolExecutionErrorHandler executionErrorHandler) {
        try {
            return toolExecutor.executeWithContext(toolRequest, invocationContext);
        } catch (Exception e) {
            ToolErrorContext errorContext = ToolErrorContext.builder()
                    .toolExecutionRequest(toolRequest)
                    .invocationContext(invocationContext)
                    .build();
            ToolErrorHandlerResult errorHandlerResult;
            if (e instanceof ToolArgumentsException) {
                errorHandlerResult = argumentsErrorHandler.handle(e.getCause(), errorContext);
            } else {
                errorHandlerResult = executionErrorHandler.handle(e.getCause(), errorContext);
            }

            return ToolExecutionResult.builder()
                    .isError(true)
                    .resultText(errorHandlerResult.text())
                    .build();
        }
    }

    private Map<ToolExecutionRequest, ToolExecutionResult> executeConcurrently(List<ToolExecutionRequest> toolRequests, Map<String, ToolExecutor> toolExecutors, InvocationContext invocationContext) {
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new LinkedHashMap<>();

        for(ToolExecutionRequest toolRequest : toolRequests) {
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                ToolExecutor toolExecutor = toolExecutors.get(cleanupToolName(toolRequest.name()));
                if (toolExecutor == null) {
                    return this.applyToolHallucinationStrategy(toolRequest);
                }
                else {
                    return executeWithErrorHandling(toolRequest,
                            toolExecutor, invocationContext,
                            this.argumentsErrorHandler,
                            this.executionErrorHandler);
                }
            }, this.executor);
            futures.put(toolRequest, future);
        }

        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();

        for(Map.Entry<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), (ToolExecutionResult)((CompletableFuture<?>)entry.getValue()).get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }

                throw new RuntimeException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        return results;
    }

    private UserMessage patchUserMessageWithSchema(UserMessage um, JsonNode responseSchema)
    {
        return UserMessage.from(
                um.singleText()
                        + ANSWER_ONLY_WITH_THE_FOLLOWING_JSON
                        + JsonHelper.createTypeDescription(responseSchema));
    }

    private ChatRequestParameters createChatRequestParameters(Config config,
                                                              ChatExecutionContext executionContext,
                                                              String rawResponseSchema,
                                                              JsonNode responseSchema) {
        if (config.isNativeJSON()) {
            return ChatRequestParameters.builder()
                    .temperature(0.0)
                    .maxOutputTokens(config.getMaximumTokens())
                    .toolChoice(ToolChoice.AUTO)
                    .toolSpecifications(executionContext.getToolService().toolSpecifications())
                    .responseFormat(JsonSchema.builder()
                            .name(JsonHelper.getSchemaName(responseSchema))
                            .rootElement(JsonRawSchema.from(rawResponseSchema))
                            .build())
                    .build();

        } else {
            return ChatRequestParameters.builder()
                    .temperature(0.0)
                    .maxOutputTokens(config.getMaximumTokens())
                    .toolChoice(ToolChoice.AUTO)
                    .toolSpecifications(executionContext.getToolService().toolSpecifications())
                    .responseFormat(ResponseFormat.TEXT)
                    .build();
        }
    }

    /**
     *
     * Execute the given chat messages include intermediate tool execution and enforce
     * a JSON response value based on the given JSON schema.
     *
     * @param config the LLMModel configuration
     * @param executionContext further parameter required for chat execution
     * @param messages the list of messages, normally a system prompt and a user prompt
     * @param rawResponseSchema the JSON schema fo the response as string
     * @param responseSchema  the JSON schema fo the response as JSON structure
     * @return a JSON structure following the schema or null
     * @throws IOException in case of errors
     */
    public JsonNode executeMessages(Config config,
                                    ChatExecutionContext executionContext,
                                    List<ChatMessage> messages,
                                    String rawResponseSchema,
                                    JsonNode responseSchema) throws IOException {
        List<ToolExecution> toolExecutions = new ArrayList<>();
        List<ChatResponse> intermediateResponses = new ArrayList<>();
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(config.getChatWindowSize());

        InvocationContext invocationContext = InvocationContext.builder()
                .build();

        ChatRequestParameters chatRequestParameters = createChatRequestParameters(config,
                executionContext,
                rawResponseSchema,
                responseSchema);

        if (!config.isNativeJSON() && !messages.isEmpty() && messages.getLast() instanceof UserMessage) {
            UserMessage um = (UserMessage) messages.removeLast();
            messages.addLast(patchUserMessageWithSchema(um, responseSchema));
        }

        chatMemory.add(messages);

        // Execute the initial request

        ChatRequest request =
                ChatRequest.builder()
                        .messages(messages)
                        .parameters(chatRequestParameters)
                        .build();

        ChatResponse chatResponse = executionContext.getChatModel().chat(request);

        chatMemory.add(chatResponse.aiMessage());

        TokenUsage aggregateTokenUsage = chatResponse.metadata().tokenUsage();

        // While the initial request requests further tool execution...loop
        while (chatResponse.aiMessage().hasToolExecutionRequests()) {
            // the last chat request was an intermediate requests, a further one has to follow
            intermediateResponses.add(chatResponse);

            Map<ToolExecutionRequest, ToolExecutionResult> toolResults = executeConcurrently(chatResponse.aiMessage().toolExecutionRequests(),
                    executionContext.getToolService().toolExecutors(),
                    invocationContext);

            for (Map.Entry<ToolExecutionRequest, ToolExecutionResult> entry : toolResults.entrySet()) {
                ToolExecutionRequest toolRequest = entry.getKey();
                ToolExecutionResult toolResult = entry.getValue();
                ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.from(toolRequest, toolResult.resultText());
                ToolExecution toolExecution = ToolExecution.builder()
                        .request(toolRequest)
                        .result(toolResult)
                        .build();

                toolExecutions.add(toolExecution);
                chatMemory.add(resultMessage);
            }

            // Initiate a further chat request, which either triggers further tool execution - or might possibly the final one
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(chatMemory.messages())
                    .parameters(chatRequestParameters)
                    .build();

            chatResponse = executionContext.getChatModel().chat(chatRequest);

            chatMemory.add(chatResponse.aiMessage());

            aggregateTokenUsage = TokenUsage.sum(aggregateTokenUsage, chatResponse.metadata().tokenUsage());
        }

        ToolServiceResult toolResult = ToolServiceResult.builder()
                .intermediateResponses(intermediateResponses)
                .finalResponse(chatResponse)
                .toolExecutions(toolExecutions)
                .aggregateTokenUsage(aggregateTokenUsage)
                .build();

        logger.info("Token usage: IN {} OUT {} TOTAL {}",
                toolResult.aggregateTokenUsage().inputTokenCount(),
                toolResult.aggregateTokenUsage().outputTokenCount(),
                toolResult.aggregateTokenUsage().totalTokenCount());

        String taskResultString = toolResult.finalResponse().aiMessage().text();

        if (taskResultString != null && !taskResultString.isEmpty()) {
            taskResultString = JsonHelper.extractJSON(taskResultString);
            try (JsonParser parser = executionContext.getMapper().getFactory().createParser(taskResultString)) {
                return executionContext.getMapper().readTree(parser);
            }
        } else {
            return null;
        }
    }
}
