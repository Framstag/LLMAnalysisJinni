package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ModelProvider;
import com.framstag.llmaj.json.ObjectMapperFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A model call that fails spends the tokens of the calls that came before it, so the failure
 * carries that usage out of the step and the step records what it really cost.
 */
public class ModelCallFailureTest {

    private static final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    private static final JsonNode SCHEMA = schema("""
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """);

    private static final String RAW_SCHEMA = SCHEMA.toString();

    @TempDir
    Path workspace;

    /**
     * A model that answers the first request and fails the second one.
     */
    private static final class FailingModel implements ChatModel {
        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            requests.add(chatRequest);

            if (requests.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"answer\":\"ok\"}"))
                        .tokenUsage(new TokenUsage(100, 20, 120))
                        .build();
            }

            throw new IllegalStateException("invalid params, context window exceeds limit");
        }
    }

    private static JsonNode schema(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ChatExecutionContext context(ChatModel model) {
        Config config = new Config();
        config.setModelName("test-model");
        // The OpenAI path asks the model twice: once for the answer and once for the answer in
        // the schema of the task. The second call is the one that fails here.
        config.setModelProvider(ModelProvider.OPENAI);

        return new ChatExecutionContext(config, model, new ToolService(),
                new ToolFilter(List.of(), List.of()), mapper, "TestTask", null, workspace);
    }

    /**
     * A model that fails every request.
     */
    private static final class AlwaysFailingModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            throw new IllegalStateException("the model is not reachable");
        }
    }

    @Test
    void aFailedModelCallCarriesTheUsageOfTheCallsThatSucceeded() {
        FailingModel model = new FailingModel();
        ChatExecutionContext execContext = context(model);

        List<ChatMessage> messages = new LinkedList<>();
        messages.add(UserMessage.from("Answer the question"));

        // The first request is answered, the second one fails.
        ModelCallFailure failure = assertThrows(ModelCallFailure.class, () ->
                new ChatExecutor().executeMessages(execContext.getConfig(), execContext, messages,
                        RAW_SCHEMA, SCHEMA));

        assertNotNull(failure.tokenUsage(), "the usage of the calls that succeeded must be carried");
        assertEquals(100, failure.tokenUsage().inputTokens());
        assertEquals(20, failure.tokenUsage().outputTokens());
        assertEquals(120, failure.tokenUsage().totalTokens());
        assertEquals("invalid params, context window exceeds limit", failure.getMessage());
        assertTrue(failure.getCause() instanceof IllegalStateException,
                "the error the model reported must be kept as the cause");
    }

    @Test
    void aFailedModelCallWithoutAnyUsageCarriesNoUsage() {
        ChatExecutionContext execContext = context(new AlwaysFailingModel());

        List<ChatMessage> messages = new LinkedList<>();
        messages.add(UserMessage.from("Answer the question"));

        ModelCallFailure failure = assertThrows(ModelCallFailure.class, () ->
                new ChatExecutor().executeMessages(execContext.getConfig(), execContext, messages,
                        RAW_SCHEMA, SCHEMA));

        assertEquals(null, failure.tokenUsage(),
                "a call that failed before it cost anything carries no usage");
    }
}
