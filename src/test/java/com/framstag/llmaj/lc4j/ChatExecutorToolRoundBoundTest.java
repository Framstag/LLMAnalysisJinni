package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.display.ProgressCallback;
import com.framstag.llmaj.json.ObjectMapperFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound on the model's tool-call rounds within one step attempt: a model that stays inside it
 * runs normally, and a model that keeps calling tools is stopped, rejected and can be attempted
 * again.
 */
public class ChatExecutorToolRoundBoundTest {

    private static final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    private static final JsonNode SCHEMA = schema("""
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """);

    private static final String RAW_SCHEMA = SCHEMA.toString();

    private static final String TOOL_NAME = "countPerWildcard";

    @TempDir
    Path workspace;

    public static class WildcardTool {
        private int executions;

        @Tool("Counts the files per wildcard")
        public String countPerWildcard(@P("The relative path to scan") String path,
                                       @P("An array of filename wildcards") List<String> wildcards) {
            executions++;

            return "counted " + wildcards.size();
        }

        int executions() {
            return executions;
        }
    }

    private static final class ScriptedModel implements ChatModel {
        private final List<AiMessage> script;
        private final List<ChatRequest> requests = new ArrayList<>();

        ScriptedModel(AiMessage... script) {
            this.script = List.of(script);
        }

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            requests.add(chatRequest);

            return ChatResponse.builder()
                    .aiMessage(script.get(Math.min(requests.size() - 1, script.size() - 1)))
                    .tokenUsage(new TokenUsage(10, 5, 15))
                    .build();
        }

        int requestCount() {
            return requests.size();
        }

        String lastUserMessage() {
            for (ChatMessage message : requests.getLast().messages().reversed()) {
                if (message instanceof UserMessage userMessage) {
                    return userMessage.singleText();
                }
            }

            return null;
        }
    }

    private static final class RecordingCallback implements ProgressCallback {
        private final List<String> retries = new ArrayList<>();

        @Override
        public void onRetry(String taskId, Integer loopIndex, int attempt, int maxAttempts, String reason) {
            retries.add(attempt + "/" + maxAttempts + " " + reason);
        }

        @Override
        public void onComplete(String taskId, Integer loopIndex) { }

        @Override public void onRequestSent(String taskId, Integer loopIndex) { }
        @Override public void onResponseReceived(String taskId, Integer loopIndex) { }
        @Override public void onToolCall(String taskId, Integer loopIndex, String toolName) { }
        @Override public void onToolResult(String taskId, Integer loopIndex, String toolName) { }
        @Override public void onTokenUsage(String taskId, Integer loopIndex, TokenUsage tokenUsage) { }
        @Override public void onError(String taskId, Integer loopIndex, String errorMessage) { }
    }

    private static JsonNode schema(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AiMessage toolCall(String wildcard) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("1")
                .name(TOOL_NAME)
                .arguments("{\"path\":\"\",\"wildcards\":[\"" + wildcard + "\"]}")
                .build());
    }

    private static AiMessage answer(String json) {
        return AiMessage.from(json);
    }

    private ToolService toolService(WildcardTool tool, int maxToolRoundTrips) {
        ToolService toolService = new ToolService();
        toolService.tools(List.of(tool));
        toolService.argumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm());
        toolService.executionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm());

        // The loop reads the bound from the service, so the test wires it the way the factory does.
        toolService.maxToolCallingRoundTrips(maxToolRoundTrips);

        return toolService;
    }

    private Config config(int maxToolRoundTrips) {
        Config config = new Config();
        config.setModelName("test-model");
        config.setMaxToolRoundTrips(maxToolRoundTrips);

        return config;
    }

    private ChatExecutionContext context(Config config, ToolService toolService, ChatModel model) {
        return new ChatExecutionContext(config, model, toolService,
                new ToolFilter(List.of(), List.of()), mapper, "TestTask", null, workspace);
    }

    private TaskStepOutcome run(ChatExecutionContext executionContext) throws IOException {
        List<ChatMessage> messages = new LinkedList<>();
        messages.add(UserMessage.from("Answer the question"));

        return new ChatExecutor().executeMessages(executionContext.getConfig(), executionContext, messages,
                RAW_SCHEMA, SCHEMA);
    }

    private TaskStepOutcome run(Config config, ToolService toolService, ChatModel model) throws IOException {
        return run(context(config, toolService, model));
    }

    @Test
    void roundsUpToTheBoundRunNormally() throws IOException {
        WildcardTool tool = new WildcardTool();
        ScriptedModel model = new ScriptedModel(
                toolCall("a"), toolCall("b"), toolCall("c"),
                answer("{\"answer\":\"ok\"}"));

        TaskStepOutcome outcome = run(config(3), toolService(tool, 3), model);

        assertTrue(outcome.isAccepted(), "three rounds are allowed by a bound of three");
        assertEquals(3, tool.executions(), "every allowed round must run");
        assertEquals(4, model.requestCount(), "three tool rounds plus the final answer request");
    }

    @Test
    void roundBeyondTheBoundIsRefusedAndRejected() throws IOException {
        WildcardTool tool = new WildcardTool();
        ScriptedModel model = new ScriptedModel(toolCall("a"), toolCall("b"), toolCall("c"));

        TaskStepOutcome outcome = run(config(2), toolService(tool, 2), model);

        assertFalse(outcome.isAccepted(), "the attempt must be rejected when the bound is reached");
        assertEquals(StepFailureReason.TOOL_ROUND_TRIPS_EXCEEDED, outcome.failure().reason());
        assertTrue(outcome.failure().message().contains("more than 2"),
                "the rejection must name the bound, got: " + outcome.failure().message());
        assertEquals(2, tool.executions(), "only the rounds inside the bound may run");
    }

    @Test
    void refusedRoundIsWrittenToTheChatLog() throws IOException {
        WildcardTool tool = new WildcardTool();
        ScriptedModel model = new ScriptedModel(toolCall("a"), toolCall("b"), toolCall("refused"));

        run(config(2), toolService(tool, 2), model);

        Path logFile = workspace.resolve("logs").resolve("TestTask.log");

        assertTrue(Files.exists(logFile), "the attempt must leave its chat log, looked for " + logFile);

        String log = Files.readString(logFile);

        assertTrue(log.contains("refused"),
                "the log must hold the tool call that was refused, so a further attempt can be told about it");
    }

    @Test
    void rejectedBoundOutcomeIsRetriedAndToldAboutTheBound() throws IOException {
        WildcardTool tool = new WildcardTool();
        Config config = config(1);
        ToolService toolService = toolService(tool, 1);
        ScriptedModel model = new ScriptedModel(toolCall("a"));
        RecordingCallback callback = new RecordingCallback();

        TaskStepRetrier retrier = new TaskStepRetrier(2, callback, "TestTask", null);

        TaskStepOutcome outcome = retrier.run((attemptNumber, repairHint) -> {
            ChatExecutionContext executionContext = context(config, toolService, model);
            executionContext.setAttemptNumber(attemptNumber);
            executionContext.setRepairHint(repairHint);

            return run(executionContext);
        });

        assertFalse(outcome.isAccepted(), "a model that keeps calling tools must end rejected");
        assertEquals(StepFailureReason.TOOL_ROUND_TRIPS_EXCEEDED, outcome.failure().reason());
        assertEquals(1, callback.retries.size(), "the first attempt must be reported as a retry");
        assertTrue(callback.retries.getFirst().contains("tool round bound exceeded"),
                "the retry report must name the reason, got: " + callback.retries.getFirst());
        assertTrue(model.lastUserMessage().contains("tool round bound exceeded"),
                "the second attempt must be told the reason, got: " + model.lastUserMessage());
    }
}
