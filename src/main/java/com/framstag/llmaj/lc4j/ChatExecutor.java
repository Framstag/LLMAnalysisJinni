package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ModelProvider;
import com.framstag.llmaj.display.ProgressCallback;
import com.framstag.llmaj.json.JsonHelper;
import com.framstag.llmaj.json.ResponsePayloadException;
import com.framstag.llmaj.json.ResponsePayloadParser;
import com.framstag.llmaj.tasks.StepTokenUsage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.networknt.schema.*;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.serialization.DefaultNodeReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class ChatExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ChatExecutor.class);

    private static final String ANSWER_ONLY_WITH_THE_FOLLOWING_JSON = "\nYou must answer strictly in the following JSON format: ";

    /**
     * How many tool names an answer to an unknown tool name may list, so the tool result stays a
     * short message even when a configuration registers many tools.
     */
    private static final int MAX_TOOL_NAMES_IN_ANSWER = 20;


    /**
     * What one attempt needs from the tool service: how tools run, how a tool error is answered,
     * how many tool rounds the model may use, and which tool names exist. It is read once per
     * attempt, so the value the workspace configuration asks for is the value the engine enforces.
     */
    private record ToolPolicy(Executor executor,
                              ToolArgumentsErrorHandler argumentsErrorHandler,
                              ToolExecutionErrorHandler executionErrorHandler,
                              int maxToolRoundTrips,
                              List<String> availableToolNames) {
    }

    private final ChatLogger chatLogger;

    public ChatExecutor() {
        chatLogger = new ChatLogger();
    }

    private static ToolPolicy createToolPolicy(ChatExecutionContext executionContext) {
        ToolService toolService = executionContext.getToolService();

        List<String> availableToolNames = executionContext.getToolFilter()
                .filter(toolService.toolSpecifications())
                .stream()
                .map(ToolSpecification::name)
                .sorted()
                .toList();

        return new ToolPolicy(toolService.effectiveToolExecutor(),
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler(),
                toolService.maxToolCallingRoundTrips(),
                availableToolNames);
    }

    private ToolExecutionResult answerUnknownToolName(ToolExecutionRequest toolRequest, ToolPolicy policy) {
        String toolNames = policy.availableToolNames().isEmpty()
                ? "there are no tools available for this task"
                : "the tools that exist are: " + String.join(", ",
                        policy.availableToolNames().subList(0,
                                Math.min(MAX_TOOL_NAMES_IN_ANSWER, policy.availableToolNames().size())));

        // The model is told what it asked for and what it may ask for instead. The step continues,
        // so a hallucinated name is a correctable mistake rather than the end of the attempt.
        String message = "There is no tool named '" + toolRequest.name() + "'; " + toolNames
                + ". Call one of those tools, or answer with the requested JSON object.";

        logger.warn("The model asked for the unknown tool '{}'; the available tools were returned to it",
                toolRequest.name());

        return ToolExecutionResult.builder().resultText(message).build();
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

    private Map<ToolExecutionRequest, ToolExecutionResult> executeConcurrently(List<ToolExecutionRequest> toolRequests, Map<String, ToolExecutor> toolExecutors, InvocationContext invocationContext, ToolPolicy policy) {
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> futures = new LinkedHashMap<>();

        for(ToolExecutionRequest toolRequest : toolRequests) {
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                ToolExecutor toolExecutor = toolExecutors.get(cleanupToolName(toolRequest.name()));
                if (toolExecutor == null) {
                    return answerUnknownToolName(toolRequest, policy);
                }
                else {
                    return ToolService.executeWithErrorHandling(toolRequest,
                            toolExecutor, invocationContext,
                            policy.argumentsErrorHandler(),
                            policy.executionErrorHandler());
                }
            }, policy.executor());
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

    private UserMessage patchUserMessage(UserMessage um,
                                         JsonNode responseSchema,
                                         String repairHint)
    {
        String patched = um.singleText()
                + ANSWER_ONLY_WITH_THE_FOLLOWING_JSON
                + JsonHelper.createTypeDescription(responseSchema);

        // A further attempt is told what was wrong with the rejected one. The hint follows the
        // schema description, because it refers to it. The rejected answer itself is deliberately
        // not echoed back: every attempt is a fresh conversation.
        if (repairHint != null && !repairHint.isBlank()) {
            patched = patched + repairHint;
        }

        return UserMessage.from(patched);
    }

    private ChatRequestParameters createInitialChatRequestParameters(Config config,
                                                                     ChatExecutionContext executionContext,
                                                                     String rawResponseSchema,
                                                                     JsonNode responseSchema) {
        if (config.getModelProvider() == ModelProvider.OLLAMA) {
            // Ollama can do tool calls and JSON Response at the same time
            if (config.isNativeJSON()) {
                // With explicit JSON schema
                return ChatRequestParameters.builder()
                        .temperature(0.0)
                        .topP(0.9)
                        .maxOutputTokens(config.getMaximumTokens())
                        .toolChoice(ToolChoice.AUTO)
                        .toolSpecifications(executionContext.getToolFilter().filter(executionContext.getToolService().toolSpecifications()))
                        .responseFormat(JsonSchema.builder()
                                .name(JsonHelper.getSchemaName(responseSchema))
                                .rootElement(JsonRawSchema.from(rawResponseSchema))
                                .build())
                        .build();

            } else {
                // With implicit JSON schema as part of the user message
                return ChatRequestParameters.builder()
                        .temperature(0.0)
                        .topP(0.9)
                        .maxOutputTokens(config.getMaximumTokens())
                        .toolChoice(ToolChoice.AUTO)
                        .toolSpecifications(executionContext.getToolFilter().filter(executionContext.getToolService().toolSpecifications()))
                        .responseFormat(ResponseFormat.TEXT)
                        .build();
            }

        }
        else if (config.getModelProvider() == ModelProvider.OPENAI) {
            // OpenAI can only do tool calls in the initial step
            return ChatRequestParameters.builder()
                    .temperature(0.0)
                    .topP(0.9)
                    .maxOutputTokens(config.getMaximumTokens())
                    .toolChoice(ToolChoice.AUTO)
                    .toolSpecifications(executionContext.getToolFilter().filter(executionContext.getToolService().toolSpecifications()))
                    .responseFormat(ResponseFormat.TEXT)
                    .build();
        }
        else {
            // LocalAI
            return ChatRequestParameters.builder()
                    //.temperature(0.0)
                    //.topP(0.9)
                    //.maxOutputTokens(config.getMaximumTokens())
                    .toolChoice(ToolChoice.AUTO)
                    .toolSpecifications(executionContext.getToolFilter().filter(executionContext.getToolService().toolSpecifications()))
                    .responseFormat(ResponseFormat.TEXT)
                    .build();
        }
    }

    private ChatRequestParameters createIntermediateChatRequestParameters(Config config,
                                                                          ChatExecutionContext executionContext,
                                                                          String rawResponseSchema,
                                                                          JsonNode responseSchema) {
        return createInitialChatRequestParameters(config,executionContext,rawResponseSchema, responseSchema);
    }

    private ChatRequestParameters createFinalChatRequestParameters(Config config,
                                                                     String rawResponseSchema,
                                                                     JsonNode responseSchema) {
        // The final call is not necessary for OLLAMA, so this is currently only for OpenAI
        // In this case we do not want to pass tool information or want to execute tools
        // We just want the JSON response

        if (config.isNativeJSON()) {
            return ChatRequestParameters.builder()
                    .temperature(0.0)
                    .topP(0.9)
                    .maxOutputTokens(config.getMaximumTokens())
                    .responseFormat(JsonSchema.builder()
                            .name(JsonHelper.getSchemaName(responseSchema))
                            .rootElement(JsonRawSchema.from(rawResponseSchema))
                            .build())
                    .build();

        } else {
            return ChatRequestParameters.builder()
                    .temperature(0.0)
                    .topP(0.9)
                    .maxOutputTokens(config.getMaximumTokens())
                    .responseFormat(ResponseFormat.TEXT)
                    .build();
        }
    }

    /**
     * Execute the given chat messages include intermediate tool execution and enforce
     * a JSON response value based on the given JSON schema.
     * </p>
     * OLLAMA and OpenAI executions models have different flexibility. OLLAMA can handle mixed tool and Response Schema
     * calls. OpenAI can either have tool or response schema requests - not both at the same time. The code tries
     * to handle both approaches.
     * </p>
     * A response the engine cannot use is reported as a rejected outcome instead of an exception, so
     * the caller can attempt the step again. Only a genuine engine failure, such as a chat log that
     * cannot be written, is thrown.
     *
     * @param config the LLMModel configuration
     * @param executionContext further parameter required for chat execution
     * @param messages the list of messages, normally a system prompt and a user prompt
     * @param rawResponseSchema the JSON schema fo the response as string
     * @param responseSchema  the JSON schema fo the response as JSON structure
     * @return the accepted payload, or the reason the attempt was rejected
     * @throws IOException in case of errors
     */
    public TaskStepOutcome executeMessages(Config config,
                                           ChatExecutionContext executionContext,
                                           List<ChatMessage> messages,
                                           String rawResponseSchema,
                                           JsonNode responseSchema) throws IOException {
        ProgressCallback callback = executionContext.getProgressCallback();
        String taskId = executionContext.getTaskId();
        Integer loopIndex = executionContext.getLoopIndex();

        // The task messages are kept out of the window: a step that needs many tool rounds would
        // otherwise push the task out of the context, and the model would answer without knowing
        // what it was asked to do.
        TaskChatMemory chatMemory = new TaskChatMemory(config.getChatWindowSize());

        InvocationContext invocationContext = InvocationContext.builder()
                .build();

        ToolPolicy toolPolicy = createToolPolicy(executionContext);

        if (!messages.isEmpty() && messages.getLast() instanceof UserMessage) {
            UserMessage um = (UserMessage) messages.removeLast();
            messages.addLast(patchUserMessage(um, responseSchema, executionContext.getRepairHint()));
        }

        chatMemory.addTask(messages);

        // Log the initial request messages to console
        if (config.isExecutionTrace()) {
            chatLogger.logProgressive(chatMemory.messages(), config.isExecutionTraceSystem());
        }

        // Execute the initial request
        callback.onRequestSent(taskId, loopIndex);

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .parameters(createInitialChatRequestParameters(config,
                        executionContext,
                        rawResponseSchema,
                        responseSchema))
                .build();

        ChatResponse chatResponse = executionContext.getChatModel().chat(request);

        callback.onResponseReceived(taskId, loopIndex);

        TokenUsage aggregateTokenUsage = chatResponse.metadata().tokenUsage();
        callback.onTokenUsage(taskId, loopIndex, aggregateTokenUsage);

        // While the initial request triggers requests for further tool execution...loop
        int toolRounds = 0;

        // The tools the model asked for, so that a step that reached the bound by asking for the
        // same thing over and over is recognisable as such in the report.
        Map<String, Integer> requestedTools = new LinkedHashMap<>();

        while (chatResponse.aiMessage().hasToolExecutionRequests()) {
            chatMemory.add(chatResponse.aiMessage());

            for (var toolRequest : chatResponse.aiMessage().toolExecutionRequests()) {
                requestedTools.merge(toolRequest.name(), 1, Integer::sum);
            }

            if (toolRounds >= toolPolicy.maxToolRoundTrips()) {
                // The round that asks beyond the bound is not executed. The attempt ends as a
                // rejection, so the step's attempt budget decides what happens next, and the chat
                // log is written first because the transcript of a runaway attempt is what a
                // further attempt is told about.
                String message = "the model requested more than " + toolPolicy.maxToolRoundTrips()
                        + " tool round(s) in one attempt"
                        + describeRequestedTools(requestedTools);

                logger.warn("Task '{}'{} reached the tool round bound of {} and was rejected",
                        taskId, loopIndex, toolPolicy.maxToolRoundTrips());

                writeChatLog(executionContext, chatMemory, aggregateTokenUsage);

                return TaskStepOutcome.rejected(
                        TaskStepFailure.of(StepFailureReason.TOOL_ROUND_TRIPS_EXCEEDED, message),
                        StepTokenUsage.from(aggregateTokenUsage));
            }

            toolRounds++;

            // Log tool calls BEFORE execution so tool's own logs come after
            if (config.isExecutionTrace()) {
                var am = chatResponse.aiMessage();
                if (am.text() != null && !am.text().isEmpty()) {
                    logger.info("< {}", am.text());
                }
                if (am.thinking() != null && !am.thinking().isEmpty()) {
                    logger.info("> Thinking: {}", am.thinking());
                }
                for (var req : am.toolExecutionRequests()) {
                    logger.info("--> Tool {}: {}", req.name(), req.arguments());
                    callback.onToolCall(taskId, loopIndex, req.name());
                }
            }

            Map<ToolExecutionRequest, ToolExecutionResult> toolResults = executeConcurrently(chatResponse.aiMessage().toolExecutionRequests(),
                    executionContext.getToolService().toolExecutors(),
                    invocationContext,
                    toolPolicy);

            // Log tool results AFTER execution
            for (Map.Entry<ToolExecutionRequest, ToolExecutionResult> entry : toolResults.entrySet()) {
                ToolExecutionRequest toolRequest = entry.getKey();
                ToolExecutionResult toolResult = entry.getValue();
                ToolExecutionResultMessage resultMessage = ToolExecutionResultMessage.from(toolRequest, toolResult.resultText());

                chatMemory.add(resultMessage);

                if (toolResult.isError()) {
                    // The message already went back to the model as the tool result. It is reported
                    // here so a tool failure is visible in the engine log and not only in the
                    // conversation the model sees.
                    logger.warn("Tool '{}' failed; the error was returned to the model so it can correct the call",
                            toolRequest.name());
                }

                if (config.isExecutionTrace()) {
                    logger.info("<-- Tool {}: {}", toolRequest.name(), toolResult.resultText());
                }
                callback.onToolResult(taskId, loopIndex, toolRequest.name());
            }

            // Advance ChatLogger past messages already logged inline
            chatLogger.advanceShownIndexTo(chatMemory.messages().size());

            // Initiate a further chat request, which might trigger further tool execution - or not
            callback.onRequestSent(taskId, loopIndex);

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(chatMemory.messages())
                    .parameters(createIntermediateChatRequestParameters(config,
                            executionContext,
                            rawResponseSchema,
                            responseSchema))
                    .build();

            chatResponse = executionContext.getChatModel().chat(chatRequest);

            callback.onResponseReceived(taskId, loopIndex);

            aggregateTokenUsage = TokenUsage.sum(aggregateTokenUsage, chatResponse.metadata().tokenUsage());
            callback.onTokenUsage(taskId, loopIndex, aggregateTokenUsage);
        }

        if (config.getModelProvider() != ModelProvider.OLLAMA) {
            // if not OLLAMA (currently only OPENAI), we must then pass JSON response schema explicitly in the final request
            // we must drop tool specification though
            request = ChatRequest.builder()
                    .messages(chatMemory.messages())
                    .parameters(createFinalChatRequestParameters(config,
                            rawResponseSchema,
                            responseSchema))
                    .build();

            if (config.isExecutionTrace()) {
                chatLogger.logProgressive(chatMemory.messages(), config.isExecutionTraceSystem());
            }

            callback.onRequestSent(taskId, loopIndex);

            chatResponse = executionContext.getChatModel().chat(request);

            callback.onResponseReceived(taskId, loopIndex);

            aggregateTokenUsage = TokenUsage.sum(aggregateTokenUsage, chatResponse.metadata().tokenUsage());
            callback.onTokenUsage(taskId, loopIndex, aggregateTokenUsage);
        }

        logger.info("Token usage: IN {} OUT {} TOTAL {} (finish reason: {})",
                aggregateTokenUsage.inputTokenCount(),
                aggregateTokenUsage.outputTokenCount(),
                aggregateTokenUsage.totalTokenCount(),
                chatResponse.finishReason());

        // Add final AI response to chat memory so it appears in logs
        chatMemory.add(chatResponse.aiMessage());

        if (config.isExecutionTrace()) {
            chatLogger.logProgressive(chatMemory.messages(), config.isExecutionTraceSystem());
        }

        // Write full conversation to log file. This happens for every attempt, so the transcript of
        // a rejected attempt is not lost when the step is attempted again.
        writeChatLog(executionContext, chatMemory, aggregateTokenUsage);

        return evaluateResponse(executionContext, chatResponse, responseSchema, aggregateTokenUsage);
    }

    /**
     * Writes the conversation of the attempt so far to its own log file.
     */
    private void writeChatLog(ChatExecutionContext executionContext,
                              TaskChatMemory chatMemory,
                              TokenUsage aggregateTokenUsage) throws IOException {
        chatLogger.writeLogFile(executionContext.getWorkspacePath(),
                executionContext.getTaskId(),
                executionContext.getLoopIndex(),
                executionContext.getAttemptNumber(),
                chatMemory.fullMessages(),
                aggregateTokenUsage);
    }

    /**
     * Turns the final model answer of one attempt into an outcome: the accepted payload when it
     * parses and conforms, otherwise the reason it was rejected.
     */
    private TaskStepOutcome evaluateResponse(ChatExecutionContext executionContext,
                                             ChatResponse chatResponse,
                                             JsonNode responseSchema,
                                             TokenUsage aggregateTokenUsage) {
        StepTokenUsage usage = StepTokenUsage.from(aggregateTokenUsage);

        String taskResultString = chatResponse.aiMessage().text();

        if (taskResultString == null || taskResultString.isBlank()) {
            // A response the model cut short has no text either, so the reason it stopped is what
            // tells the two apart.
            if (isTruncated(chatResponse)) {
                logger.warn("The model stopped in the middle of its answer for task '{}'",
                        executionContext.getTaskId());

                return TaskStepOutcome.rejected(TaskStepFailure.of(StepFailureReason.RESPONSE_TRUNCATED,
                        truncatedMessage(chatResponse)), usage);
            }

            logger.warn("The model returned no response text for task '{}'", executionContext.getTaskId());

            return TaskStepOutcome.rejected(TaskStepFailure.of(StepFailureReason.NO_RESPONSE,
                    "the model returned no response text"), usage);
        }

        if (isTruncated(chatResponse)) {
            // The model stopped in the middle of its answer. The text that is there is a part of the
            // answer, not the answer, so it is reported as truncated and not as a payload that does
            // not parse: the condition of a half answer is not the condition of a wrong one.
            logger.warn("The model stopped in the middle of its answer for task '{}'",
                    executionContext.getTaskId());

            return TaskStepOutcome.rejected(TaskStepFailure.of(StepFailureReason.RESPONSE_TRUNCATED,
                    truncatedMessage(chatResponse)), usage);
        }

        JsonNode result;

        try {
            result = new ResponsePayloadParser(executionContext.getMapper()).parse(taskResultString);
        } catch (ResponsePayloadException e) {
            // The parser tells a response that never had a payload from one whose payload is
            // malformed, so the two are reported differently. Its message and its bounded excerpt
            // are what a further attempt is told.
            StepFailureReason reason =
                    e.getCondition() == ResponsePayloadException.Condition.NO_PAYLOAD_LOCATED
                            ? StepFailureReason.NO_PAYLOAD
                            : StepFailureReason.PAYLOAD_NOT_PARSEABLE;

            logger.warn("The response of task '{}' cannot be used: {}",
                    executionContext.getTaskId(), e.getMessage());

            return TaskStepOutcome.rejected(TaskStepFailure.of(reason, e.getMessage(), e.getExcerpt()), usage);
        }

        List<String> violations = validateAgainstSchema(executionContext, result, responseSchema);

        if (!violations.isEmpty()) {
            logger.warn("LLM response does not conform to JSON schema ({} errors):", violations.size());
            for (String violation : violations) {
                logger.warn("  Schema violation: {}", violation);
            }

            return TaskStepOutcome.rejected(TaskStepFailure.schemaViolation(violations), usage);
        }

        return TaskStepOutcome.accepted(result, usage);
    }

    /**
     * Names the tools the model asked for and how often it asked for them, so that a step that
     * reached the bound by asking for one file after another is recognisable as such.
     * <p>
     * The description is bounded, because the reason is shown on a line of the display that holds
     * one line only.
     *
     * @param requestedTools the tools the model asked for, with their counts
     * @return the description of the tools, or an empty string when the model asked for none
     */
    private static String describeRequestedTools(Map<String, Integer> requestedTools) {
        if (requestedTools.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder("; it asked for ");

        int remaining = MAX_TOOL_NAMES_IN_ANSWER;
        boolean first = true;

        for (Map.Entry<String, Integer> entry : requestedTools.entrySet()) {
            if (remaining <= 0) {
                sb.append(", and ").append(requestedTools.size() - MAX_TOOL_NAMES_IN_ANSWER)
                        .append(" more tool(s)");
                break;
            }

            if (!first) {
                sb.append(", ");
            }

            sb.append(entry.getKey()).append(" x").append(entry.getValue());

            first = false;
            remaining--;
        }

        return sb.toString();
    }

    /**
     * True when the model stopped in the middle of its answer, so the response is cut short.
     * <p>
     * A model that ran out of output budget says so in the reason it stopped. A response that was
     * cut short is not a wrong answer, it is a part of an answer, and the two are reported
     * differently.
     *
     * @param chatResponse the response of the model
     * @return true when the model did not finish its answer
     */
    private static boolean isTruncated(ChatResponse chatResponse) {
        FinishReason finishReason = chatResponse.finishReason();

        return finishReason == FinishReason.LENGTH;
    }

    /**
     * The message of a truncated response, naming the reason the model gave for stopping.
     *
     * @param chatResponse the response of the model
     * @return the message that is reported for the truncated response
     */
    private static String truncatedMessage(ChatResponse chatResponse) {
        FinishReason finishReason = chatResponse.finishReason();

        return "the model stopped before it finished its answer (reason: "
                + (finishReason == null ? "unknown" : finishReason.name().toLowerCase(Locale.ROOT)) + ")";
    }

    /**
     * Validates the payload that is about to be published against the declared response schema and
     * returns the violation messages, or an empty list when the payload conforms or the validator
     * itself cannot run.
     */
    private List<String> validateAgainstSchema(ChatExecutionContext executionContext,
                                               JsonNode result,
                                               JsonNode responseSchema) {
        if (responseSchema == null) {
            return List.of();
        }

        try {
            String payloadString = executionContext.getMapper().writeValueAsString(result);

            SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(
                    Dialects.getDraft202012(),
                    builder -> builder.nodeReader(DefaultNodeReader.Builder::locationAware));
            String schemaString = executionContext.getMapper().writeValueAsString(responseSchema);
            Schema schema = schemaRegistry.getSchema(schemaString, InputFormat.JSON);

            List<com.networknt.schema.Error> errors = schema.validate(payloadString, InputFormat.JSON);

            // Report on the parts of an error the engine can name itself: the validator's message is
            // written in the language of this machine and does not say where or what was wrong.
            return SchemaViolationReport.of(errors);
        } catch (Exception e) {
            // The validator could not run, which says nothing about the payload, so the payload is
            // accepted with a diagnostic instead of failing a step the engine cannot judge.
            logger.warn("Could not validate response against schema: {}", e.getMessage());

            return List.of();
        }
    }
}
