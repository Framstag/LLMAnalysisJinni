package com.framstag.llmaj.lc4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.internal.Exceptions;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    return executeWithErrorHandling(toolRequest, toolExecutor, invocationContext, this.argumentsErrorHandler, this.executionErrorHandler);
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

    /**
     *
     * @param chatResponse THe initial chat response
     * @param parameters potential parameters
     * @param chatModel the chat model to be used
     * @param chatMemory chat memory object (!=null)
     * @param invocationContext invocation context
     * @param toolExecutors map of tool names to executors
     * @return the result
     */
    public ToolServiceResult execute(ChatResponse chatResponse,
                                     ChatRequestParameters parameters,
                                     ChatModel chatModel,
                                     ChatMemory chatMemory,
                                     InvocationContext invocationContext,
                                     Map<String, ToolExecutor> toolExecutors) {

        TokenUsage aggregateTokenUsage = chatResponse.metadata().tokenUsage();
        List<ToolExecution> toolExecutions = new ArrayList<>();
        List<ChatResponse> intermediateResponses = new ArrayList<>();

        for(int executionsLeft = this.maxSequentialToolsInvocations; executionsLeft-- != 0; ) {

            AiMessage aiMessage = chatResponse.aiMessage();
            chatMemory.add(aiMessage);

            if (!aiMessage.hasToolExecutionRequests()) {
                return ToolServiceResult.builder()
                        .intermediateResponses(intermediateResponses)
                        .finalResponse(chatResponse)
                        .toolExecutions(toolExecutions)
                        .aggregateTokenUsage(aggregateTokenUsage)
                        .build();
            }

            intermediateResponses.add(chatResponse);
            Map<ToolExecutionRequest, ToolExecutionResult> toolResults = executeConcurrently(aiMessage.toolExecutionRequests(),
                    toolExecutors,
                    invocationContext);

            for(Map.Entry<ToolExecutionRequest, ToolExecutionResult> entry : toolResults.entrySet()) {
                ToolExecutionRequest request = entry.getKey();
                ToolExecutionResult result = entry.getValue();
                ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.from(request, result.resultText());
                ToolExecution toolExecution = ToolExecution.builder()
                        .request(request)
                        .result(result)
                        .build();
                toolExecutions.add(toolExecution);
                chatMemory.add(resultMessage);
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(chatMemory.messages())
                    .parameters(parameters)
                    .build();
            chatResponse = chatModel.chat(chatRequest);

            aggregateTokenUsage = TokenUsage.sum(aggregateTokenUsage, chatResponse.metadata().tokenUsage());
        }

        throw Exceptions.runtime("Something is wrong, exceeded %s sequential tool executions",
                this.maxSequentialToolsInvocations);
    }
}
