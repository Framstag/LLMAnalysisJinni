package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives a real analysis against a stub model server, so the token usage the engine records in the
 * execution state is verified end to end: the counts of a non-loop task, the counts per loop
 * index, and the counts of a task that was retried.
 */
public class AnalyseCmdTokenUsageTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String SCHEMA = """
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """;

    private static final String TWO_MODULES = """
            {"modules":{"modules":[{"name":"first"},{"name":"second"}]}}
            """;

    /**
     * The counts the stub model server reports: ten input tokens and five output tokens per call.
     */
    private static final int INPUT_TOKENS = 10;
    private static final int OUTPUT_TOKENS = 5;

    @TempDir
    Path projectDirectory;

    @TempDir
    Path analysisDirectory;

    @TempDir
    Path workspaceDirectory;

    private HttpServer server;
    private final List<String> answers = new CopyOnWriteArrayList<>();
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();

    private Level previousRootLevel;
    private List<Appender<ILoggingEvent>> previousRootAppenders;

    /**
     * A run installs the engine log routing, which changes the level of the root logger and detaches
     * the console appenders. The state is restored, so a test that runs an analysis does not change
     * what the tests that run after it see.
     */
    @BeforeEach
    void captureLogbackState() {
        Logger root = rootLogger();
        previousRootLevel = root.getLevel();
        previousRootAppenders = new ArrayList<>();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            previousRootAppenders.add(iterator.next());
        }
    }

    @AfterEach
    void restoreLogbackState() {
        Logger root = rootLogger();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            Appender<ILoggingEvent> appender = iterator.next();

            if (!previousRootAppenders.contains(appender)) {
                root.detachAppender(appender);
                appender.stop();
            }
        }

        for (Appender<ILoggingEvent> appender : previousRootAppenders) {
            if (!attached(root).contains(appender)) {
                root.addAppender(appender);
            }
        }

        root.setLevel(previousRootLevel);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static List<Appender<ILoggingEvent>> attached(Logger root) {
        List<Appender<ILoggingEvent>> result = new ArrayList<>();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            result.add(iterator.next());
        }

        return result;
    }

    private static Logger rootLogger() {
        return ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private int startStubModel(String... scriptedAnswers) throws IOException {
        answers.clear();
        answers.addAll(List.of(scriptedAnswers));

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handleModelRequest);
        server.start();

        return server.getAddress().getPort();
    }

    private void handleModelRequest(HttpExchange exchange) throws IOException {
        requestPaths.add(exchange.getRequestURI().getPath());

        exchange.getRequestBody().readAllBytes();

        int served = Math.max(0, requestPaths.size() - 1);
        String answer = answers.get(Math.min(served, answers.size() - 1));

        String body = """
                {"model":"test-model",
                 "created_at":"2024-01-01T00:00:00.000Z",
                 "message":{"role":"assistant","content":%s},
                 "done":true,
                 "done_reason":"stop",
                 "total_duration":1000,
                 "load_duration":0,
                 "prompt_eval_count":10,
                 "prompt_eval_duration":100,
                 "eval_count":5,
                 "eval_duration":100}
                """.formatted(mapper.writeValueAsString(answer));

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void writeAnalysis(String tasksYaml) throws IOException {
        Files.writeString(analysisDirectory.resolve("tasks.yaml"), tasksYaml);

        Files.createDirectories(analysisDirectory.resolve("prompts"));
        Files.writeString(analysisDirectory.resolve("prompts/single.md"), "Answer the question.");

        Files.createDirectories(analysisDirectory.resolve("results"));
        Files.writeString(analysisDirectory.resolve("results/Single.json"), SCHEMA);
    }

    private void writeConfig(int port, int retries) throws IOException {
        Files.writeString(workspaceDirectory.resolve("config.json"), """
                {
                  "modelProvider": "ollama",
                  "modelURL": "http://localhost:%d",
                  "modelName": "test-model",
                  "projectDirectory": %s,
                  "analysisDirectory": %s,
                  "retries": %d
                }
                """.formatted(port,
                mapper.writeValueAsString(projectDirectory.toString()),
                mapper.writeValueAsString(analysisDirectory.toString()),
                retries));
    }

    private void writeModules(String analysisState) throws IOException {
        Files.writeString(workspaceDirectory.resolve("analysis.json"), analysisState);
    }

    private int runAnalysis() {
        AnalyseCmd analyseCmd = new AnalyseCmd();
        new CommandLine(analyseCmd).parseArgs(workspaceDirectory.toString());

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        try {
            return analyseCmd.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(originalOut);
        }
    }

    private JsonNode stateOf(String taskId) throws IOException {
        Path stateFile = workspaceDirectory.resolve("state.json");

        if (!Files.exists(stateFile)) {
            return null;
        }

        for (JsonNode state : mapper.readTree(stateFile.toFile())) {
            if (taskId.equals(state.path("taskId").asText())) {
                return state;
            }
        }

        return null;
    }

    private static final String ONE_TASK = """
            ---
            id: SingleTask
            name: Single Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: result
            active: true
            """;

    @Test
    void theUsageOfANonLoopTaskIsRecordedInTheState() throws Exception {
        int port = startStubModel("{\"answer\":\"ok\"}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 1);

        assertEquals(0, runAnalysis(), "the run must complete");

        JsonNode usage = stateOf("SingleTask").path("tokenUsage");

        assertEquals(INPUT_TOKENS, usage.path("inputTokens").asInt(),
                "the state must hold the input count the model reported");
        assertEquals(OUTPUT_TOKENS, usage.path("outputTokens").asInt());
        assertEquals(INPUT_TOKENS + OUTPUT_TOKENS, usage.path("totalTokens").asInt());
    }

    private static final String ONE_LOOP_TASK = """
            ---
            id: LoopTask
            name: Loop Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: loopResult
            active: true
            loopOn: /modules/modules
            """;

    @Test
    void theUsageOfALoopTaskIsRecordedPerIndex() throws Exception {
        int port = startStubModel("{\"answer\":\"ok\"}");
        writeAnalysis(ONE_LOOP_TASK);
        writeConfig(port, 1);
        writeModules(TWO_MODULES);

        assertEquals(0, runAnalysis(), "the run must complete");

        JsonNode state = stateOf("LoopTask");
        JsonNode indexUsages = state.path("indexTokenUsage");

        assertEquals(2, indexUsages.size(), "every loop index must carry its own usage");

        for (JsonNode indexUsage : indexUsages) {
            assertEquals(INPUT_TOKENS, indexUsage.path("inputTokens").asInt(),
                    "the index must hold the input count it cost");
            assertEquals(OUTPUT_TOKENS, indexUsage.path("outputTokens").asInt());
        }

        assertEquals(2 * INPUT_TOKENS, state.path("tokenUsage").path("inputTokens").asInt(),
                "the usage of a loop task is the sum of the usages of its indices");
        assertEquals(2 * OUTPUT_TOKENS, state.path("tokenUsage").path("outputTokens").asInt());
    }

    @Test
    void theUsageOfARetriedTaskIsTheSumOfItsAttempts() throws Exception {
        // The first answer is rejected, the second one is accepted, so the task is attempted twice.
        int port = startStubModel("{\"answer\": 42}", "{\"answer\":\"ok\"}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 3);

        assertEquals(0, runAnalysis(), "the run must complete");

        assertEquals(2, requestPaths.size(), "the task must be attempted twice");

        JsonNode usage = stateOf("SingleTask").path("tokenUsage");

        assertEquals(2 * INPUT_TOKENS, usage.path("inputTokens").asInt(),
                "a retried task reports what it really cost");
        assertEquals(2 * OUTPUT_TOKENS, usage.path("outputTokens").asInt());
        assertEquals(2 * (INPUT_TOKENS + OUTPUT_TOKENS), usage.path("totalTokens").asInt());
    }

    @Test
    void theUsageOfAFailedTaskIsKept() throws Exception {
        // Every attempt is rejected, so the task fails but its attempts cost tokens.
        int port = startStubModel("{\"answer\": 42}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 2);

        assertEquals(0, runAnalysis(), "the run must complete even though the task failed");

        assertEquals("FAILED", stateOf("SingleTask").path("state").asText());

        JsonNode usage = stateOf("SingleTask").path("tokenUsage");

        assertEquals(2 * INPUT_TOKENS, usage.path("inputTokens").asInt(),
                "a failed task keeps the usage of its attempts");
        assertEquals(2 * OUTPUT_TOKENS, usage.path("outputTokens").asInt());
    }
}
