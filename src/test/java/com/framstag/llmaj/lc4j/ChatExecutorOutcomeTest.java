package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What one attempt of a step returns: the accepted payload, or the reason the answer cannot be
 * used. A response the engine rejects is an ordinary outcome, not an exception, so the caller can
 * attempt the step again.
 */
public class ChatExecutorOutcomeTest {

    private static final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    private static final JsonNode SCHEMA = schema("""
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """);

    private static final String RAW_SCHEMA = SCHEMA.toString();

    @TempDir
    Path workspace;

    /**
     * A model that answers every request with the same text and records what it was asked.
     */
    private static final class CannedModel implements ChatModel {
        private final String answer;
        private final List<ChatRequest> requests = new ArrayList<>();

        CannedModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            requests.add(chatRequest);

            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(answer))
                    .tokenUsage(new TokenUsage(10, 5, 15))
                    .build();
        }

        List<ChatRequest> requests() {
            return requests;
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

        return new ChatExecutionContext(config, model, new ToolService(),
                new ToolFilter(List.of(), List.of()), mapper, "TestTask", null, workspace);
    }

    private TaskStepOutcome run(ChatExecutionContext execContext, String prompt) throws IOException {
        List<ChatMessage> messages = new LinkedList<>();
        messages.add(UserMessage.from(prompt));

        return new ChatExecutor().executeMessages(execContext.getConfig(), execContext, messages,
                RAW_SCHEMA, SCHEMA);
    }

    private TaskStepOutcome run(String answer, String prompt) throws IOException {
        return run(context(new CannedModel(answer)), prompt);
    }

    @Test
    void conformantAnswerIsAccepted() throws IOException {
        TaskStepOutcome outcome = run("{\"answer\":\"ok\"}", "Answer the question");

        assertTrue(outcome.isAccepted());
        assertEquals("ok", outcome.payload().path("answer").asText());
    }

    @Test
    void payloadInsideProseOrAFenceIsAccepted() throws IOException {
        TaskStepOutcome outcome = run("Sure, here it is:\n```json\n{\"answer\":\"ok\"}\n```\n",
                "Answer the question");

        assertTrue(outcome.isAccepted());
        assertEquals("ok", outcome.payload().path("answer").asText());
    }

    @Test
    void emptyAnswerIsReportedAsNoResponse() throws IOException {
        TaskStepOutcome outcome = run("", "Answer the question");

        assertFalse(outcome.isAccepted());
        assertEquals(StepFailureReason.NO_RESPONSE, outcome.failure().reason());
    }

    @Test
    void answerWithoutPayloadIsReportedAsNoPayload() throws IOException {
        TaskStepOutcome outcome = run("I am sorry, I cannot do that.", "Answer the question");

        assertFalse(outcome.isAccepted());
        assertEquals(StepFailureReason.NO_PAYLOAD, outcome.failure().reason());
        assertNotNull(outcome.failure().excerpt());
    }

    @Test
    void answerWithAnUnparseablePayloadIsReportedAsParseFailure() throws IOException {
        TaskStepOutcome outcome = run("{\"answer\": \"a\",}", "Answer the question");

        assertFalse(outcome.isAccepted());
        assertEquals(StepFailureReason.PAYLOAD_NOT_PARSEABLE, outcome.failure().reason());
        assertTrue(outcome.failure().message().contains("not parseable"),
                "the parser message must survive, got: " + outcome.failure().message());
    }

    @Test
    void answerViolatingTheSchemaIsReportedWithItsViolations() throws IOException {
        TaskStepOutcome outcome = run("{\"answer\": 42}", "Answer the question");

        assertFalse(outcome.isAccepted());
        assertEquals(StepFailureReason.SCHEMA_VIOLATION, outcome.failure().reason());
        assertFalse(outcome.failure().violationMessages().isEmpty(),
                "the violations are what a further attempt is told");
    }

    @Test
    void answerMissingARequiredPropertyIsReportedAsViolation() throws IOException {
        TaskStepOutcome outcome = run("{}", "Answer the question");

        assertFalse(outcome.isAccepted());
        assertEquals(StepFailureReason.SCHEMA_VIOLATION, outcome.failure().reason());
    }

    @Test
    void aViolationIsReportedInsteadOfThrown() {
        // The old behaviour logged a warning and returned the payload, so a caller could not react.
        assertDoesNotThrow(() -> run("{\"answer\": 42}", "Answer the question"));
    }

    @Test
    void repairHintFollowsTheSchemaDescription() throws IOException {
        CannedModel model = new CannedModel("{\"answer\":\"ok\"}");
        ChatExecutionContext execContext = context(model);
        execContext.setRepairHint("\nYour previous answer was rejected: schema violation (x).");

        run(execContext, "Answer the question");

        String userText = lastUserMessage(model);

        assertTrue(userText.contains("You must answer strictly in the following JSON format"),
                "the schema description must still be there, got: " + userText);
        assertTrue(userText.contains("Your previous answer was rejected"),
                "the hint must reach the model, got: " + userText);
        assertTrue(userText.indexOf("You must answer strictly in the following JSON format")
                        < userText.indexOf("Your previous answer was rejected"),
                "the hint refers to the schema, so it must follow the schema description");
    }

    @Test
    void firstAttemptCarriesNoRepairHint() throws IOException {
        CannedModel model = new CannedModel("{\"answer\":\"ok\"}");

        run(context(model), "Answer the question");

        assertFalse(lastUserMessage(model).contains("rejected"),
                "the first attempt must not claim a previous answer was rejected");
    }

    @Test
    void rejectedAnswerIsNotEchoedBack() throws IOException {
        CannedModel model = new CannedModel("{\"answer\":\"ok\"}");
        ChatExecutionContext execContext = context(model);
        execContext.setRepairHint("\nYour previous answer was rejected: no JSON payload (prose only).");

        run(execContext, "Answer the question");

        assertFalse(lastUserMessage(model).contains("I am sorry"),
                "every attempt is a fresh conversation, the rejected answer must not reappear");
    }

    @Test
    void everyAttemptWritesItsOwnLogFile() throws IOException {
        ChatExecutionContext firstAttempt = context(new CannedModel("{\"answer\": \"a\",}"));
        run(firstAttempt, "Answer the question");

        ChatExecutionContext secondAttempt = context(new CannedModel("{\"answer\":\"ok\"}"));
        secondAttempt.setAttemptNumber(2);
        secondAttempt.setRepairHint("hint");
        run(secondAttempt, "Answer the question");

        assertTrue(Files.exists(workspace.resolve("logs").resolve("TestTask.log")),
                "the first attempt keeps the plain name");
        assertTrue(Files.exists(workspace.resolve("logs").resolve("TestTask.attempt2.log")),
                "a further attempt carries its attempt number");
    }

    private static String lastUserMessage(CannedModel model) {
        List<ChatMessage> messages = model.requests().getLast().messages();

        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }

        fail("the request carried no user message");

        return "";
    }
}
