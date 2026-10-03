package com.framstag.llmaj.lc4j;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.json.ObjectMapperFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the tool loop does with tool calls it cannot carry out: an unusable argument and a tool name
 * that does not exist are answered as tool results, and the step keeps running so the model can
 * correct itself.
 */
public class ChatExecutorToolErrorTest {

    private static final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    private static final JsonNode SCHEMA = schema("""
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """);

    private static final String RAW_SCHEMA = SCHEMA.toString();

    /** The tool name is the method name of the annotated method. */
    private static final String TOOL_NAME = "countPerWildcard";

    @TempDir
    Path workspace;

    /**
     * A tool with an array-typed parameter, which is the shape a model serialises as a bare string
     * when it does not emit a single-element array.
     */
    public static class WildcardTool {
        private final List<List<String>> receivedWildcards = new ArrayList<>();

        @Tool("Counts the files per wildcard")
        public String countPerWildcard(@P("The relative path to scan") String path,
                                       @P("An array of filename wildcards") List<String> wildcards) {
            receivedWildcards.add(List.copyOf(wildcards));

            return "counted " + wildcards.size();
        }

        int executions() {
            return receivedWildcards.size();
        }

        List<List<String>> receivedWildcards() {
            return receivedWildcards;
        }
    }

    /** A tool that raises while it runs, which must be answered as a tool result too. */
    public static class FailingTool {
        @Tool("Always fails")
        public String alwaysFails(@P("The relative path to scan") String path) {
            throw new IllegalStateException("the tool cannot read " + path);
        }
    }

    /**
     * A model that answers with the next scripted message and records every request it was given.
     */
    private static final class ScriptedModel implements ChatModel {
        private final List<AiMessage> script;
        private final List<ChatRequest> requests = new ArrayList<>();

        ScriptedModel(AiMessage... script) {
            this.script = List.of(script);
        }

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            requests.add(chatRequest);

            AiMessage answer = script.get(Math.min(requests.size() - 1, script.size() - 1));

            return ChatResponse.builder()
                    .aiMessage(answer)
                    .tokenUsage(new TokenUsage(10, 5, 15))
                    .build();
        }

        int requestCount() {
            return requests.size();
        }

        /**
         * The text of every tool result the model was given across all requests.
         */
        List<String> toolResultTexts() {
            List<String> texts = new ArrayList<>();

            for (ChatRequest request : requests) {
                for (ChatMessage message : request.messages()) {
                    if (message instanceof ToolExecutionResultMessage toolResult) {
                        texts.add(toolResult.text());
                    }
                }
            }

            return texts;
        }
    }

    private static JsonNode schema(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AiMessage toolCall(String name, String arguments) {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("1")
                .name(name)
                .arguments(arguments)
                .build());
    }

    private static AiMessage answer(String json) {
        return AiMessage.from(json);
    }

    private ToolService toolService(Object tool) {
        ToolService toolService = new ToolService();
        toolService.tools(List.of(tool));

        // The same wiring ToolServiceFactory applies: both tool error paths answer the model.
        toolService.argumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm());
        toolService.executionErrorHandler(ToolExecutionErrorHandler.sendExceptionMessageToLlm());

        return toolService;
    }

    private TaskStepOutcome run(ToolService toolService, ChatModel model) throws IOException {
        Config config = new Config();
        config.setModelName("test-model");

        ChatExecutionContext executionContext = new ChatExecutionContext(config, model, toolService,
                new ToolFilter(List.of(), List.of()), mapper, "TestTask", null, workspace);

        List<ChatMessage> messages = new LinkedList<>();
        messages.add(UserMessage.from("Answer the question"));

        return new ChatExecutor().executeMessages(config, executionContext, messages, RAW_SCHEMA, SCHEMA);
    }

    @Test
    void unusableArgumentBecomesAToolResultInsteadOfEndingTheStep() throws IOException {
        WildcardTool tool = new WildcardTool();
        ScriptedModel model = new ScriptedModel(
                toolCall(TOOL_NAME, "{\"path\":\"\",\"wildcards\":\"pom.xml\"}"),
                answer("{\"answer\":\"ok\"}"));

        TaskStepOutcome outcome = run(toolService(tool), model);

        assertTrue(outcome.isAccepted(), "the step must continue after an argument error");
        assertEquals(0, tool.executions(), "the tool must not run with arguments it cannot use");
        assertEquals(2, model.requestCount(), "the model must be asked again");
        assertFalse(model.toolResultTexts().isEmpty(), "the model must receive a tool result");
        String resultText = model.toolResultTexts().getFirst();
        assertTrue(resultText.contains("ArrayList"),
                "the tool result must name the type that could not be constructed, got: " + resultText);
        assertTrue(resultText.contains("pom.xml"),
                "the tool result must name the value the model supplied, got: " + resultText);
    }

    @Test
    void correctedCallIsExecuted() throws IOException {
        WildcardTool tool = new WildcardTool();
        ScriptedModel model = new ScriptedModel(
                toolCall(TOOL_NAME, "{\"path\":\"\",\"wildcards\":\"pom.xml\"}"),
                toolCall(TOOL_NAME, "{\"path\":\"\",\"wildcards\":[\"pom.xml\"]}"),
                answer("{\"answer\":\"ok\"}"));

        TaskStepOutcome outcome = run(toolService(tool), model);

        assertTrue(outcome.isAccepted());
        assertEquals(1, tool.executions(), "the corrected call must reach the tool");
        assertEquals(List.of(List.of("pom.xml")), tool.receivedWildcards());
    }

    @Test
    void unknownToolNameIsAnsweredWithTheToolsThatExist() throws IOException {
        WildcardTool tool = new WildcardTool();
        ScriptedModel model = new ScriptedModel(
                toolCall("no_such_tool", "{}"),
                answer("{\"answer\":\"ok\"}"));

        TaskStepOutcome outcome = run(toolService(tool), model);

        assertTrue(outcome.isAccepted(), "the step must continue after a hallucinated tool name");
        assertEquals(0, tool.executions(), "no tool must run for an unknown name");
        assertEquals(1, model.toolResultTexts().size(), "the model must receive one tool result");
        assertTrue(model.toolResultTexts().getFirst().contains("no_such_tool"),
                "the tool result must name what was asked for, got: " + model.toolResultTexts().getFirst());
        assertTrue(model.toolResultTexts().getFirst().contains(TOOL_NAME),
                "the tool result must name the tools that exist, got: " + model.toolResultTexts().getFirst());
    }

    @Test
    void toolThatRaisesIsAnsweredAsAToolResult() throws IOException {
        ScriptedModel model = new ScriptedModel(
                toolCall("alwaysFails", "{\"path\":\"pom.xml\"}"),
                answer("{\"answer\":\"ok\"}"));

        TaskStepOutcome outcome = run(toolService(new FailingTool()), model);

        assertTrue(outcome.isAccepted(), "the step must continue after a tool that raised");
        assertEquals(2, model.requestCount(), "the model must be asked again");
        assertTrue(model.toolResultTexts().getFirst().contains("the tool cannot read pom.xml"),
                "the tool result must carry the tool's error, got: " + model.toolResultTexts().getFirst());
    }

    @Test
    void toolErrorReturnedToTheModelIsReported() throws IOException {
        WildcardTool tool = new WildcardTool();
        ScriptedModel model = new ScriptedModel(
                toolCall(TOOL_NAME, "{\"path\":\"\",\"wildcards\":\"pom.xml\"}"),
                answer("{\"answer\":\"ok\"}"));

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ChatExecutor.class);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);

        MDC.put("taskId", "TestTask");

        try {
            run(toolService(tool), model);
        } finally {
            MDC.remove("taskId");
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }

        List<ILoggingEvent> records = appender.list;

        assertEquals(1, records.size(), "exactly one record per error result, got: " + records);
        assertEquals(Level.WARN, records.getFirst().getLevel());
        assertTrue(records.getFirst().getFormattedMessage().contains(TOOL_NAME),
                "the record must name the tool, got: " + records.getFirst().getFormattedMessage());
        assertTrue(records.getFirst().getFormattedMessage().contains("returned to the model"),
                "the record must say the model was told, got: " + records.getFirst().getFormattedMessage());
        assertEquals("TestTask", records.getFirst().getMDCPropertyMap().get("taskId"),
                "the record must carry the task identifier");
    }
}
