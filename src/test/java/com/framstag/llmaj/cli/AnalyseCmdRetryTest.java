package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.Level;
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
 * Drives a real analysis against a stub model server, so that the wiring of the retry budget — which
 * outcome is stored, which task status is written, and how often the model is asked — is verified
 * end to end and not only in the retry policy's unit tests.
 */
public class AnalyseCmdRetryTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String SCHEMA = """
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """;

    @TempDir
    Path projectDirectory;

    @TempDir
    Path analysisDirectory;

    @TempDir
    Path workspaceDirectory;

    private HttpServer server;
    private final List<String> answers = new CopyOnWriteArrayList<>();
    private final List<String> requestPaths = new CopyOnWriteArrayList<>();
    private int responseStatus = 200;

    private Level previousRootLevel;
    private List<Appender<ILoggingEvent>> previousRootAppenders;

    /**
     * A real run installs the engine's log routing, which changes global logback state (level and
     * appenders). This test drives runs in the same JVM as every other test, so the state is put
     * back afterwards.
     */
    @BeforeEach
    void captureLogbackState() {
        ch.qos.logback.classic.Logger root = rootLogger();
        previousRootLevel = root.getLevel();
        previousRootAppenders = new ArrayList<>();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            previousRootAppenders.add(iterator.next());
        }
    }

    @AfterEach
    void restoreLogbackState() {
        ch.qos.logback.classic.Logger root = rootLogger();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            Appender<ILoggingEvent> appender = iterator.next();

            if (!previousRootAppenders.contains(appender)) {
                root.detachAppender(appender);
                appender.stop();
            }
        }

        for (Appender<ILoggingEvent> appender : previousRootAppenders) {
            if (!attachedAppenders(root).contains(appender)) {
                root.addAppender(appender);
            }
        }

        root.setLevel(previousRootLevel);
    }

    private static List<Appender<ILoggingEvent>> attachedAppenders(ch.qos.logback.classic.Logger root) {
        List<Appender<ILoggingEvent>> result = new ArrayList<>();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            result.add(iterator.next());
        }

        return result;
    }

    private static ch.qos.logback.classic.Logger rootLogger() {
        return ((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private int startStubModel(String... scriptedAnswers) throws IOException {
        answers.clear();
        answers.addAll(List.of(scriptedAnswers));
        responseStatus = 200;

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handleModelRequest);
        server.start();

        return server.getAddress().getPort();
    }

    /**
     * A model server that answers every request with the given HTTP status, as a broken or
     * misconfigured endpoint would.
     */
    private int startFailingModel(int status) throws IOException {
        answers.clear();
        answers.add("the model server refused the request");
        responseStatus = status;

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handleModelRequest);
        server.start();

        return server.getAddress().getPort();
    }

    private void handleModelRequest(HttpExchange exchange) throws IOException {
        requestPaths.add(exchange.getRequestURI().getPath());

        // Drain the body so the client sees its request completed.
        exchange.getRequestBody().readAllBytes();

        int served = Math.max(0, requestPaths.size() - 1);
        String answer = answers.get(Math.min(served, answers.size() - 1));

        String body = responseStatus == 200
                ? """
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
                """.formatted(mapper.writeValueAsString(answer))
                : """
                {"error":%s}
                """.formatted(mapper.writeValueAsString(answer));

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);

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

    private static final String ONE_TASK = """
            ---
            id: SingleTask
            name: Single Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: singleResult
            active: true
            tags:
              - single_done
            """;

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
        JsonNode states = mapper.readTree(workspaceDirectory.resolve("state.json").toFile());

        for (JsonNode state : states) {
            if (taskId.equals(state.path("taskId").asText())) {
                return state;
            }
        }

        return null;
    }

    private JsonNode analysisState() throws IOException {
        return mapper.readTree(workspaceDirectory.resolve("analysis.json").toFile());
    }

    /**
     * True when an analysis state exists and carries the given response property. A run in which
     * nothing was accepted never creates the file at all.
     */
    private boolean hasStoredResult(String responseProperty) throws IOException {
        Path analysisFile = workspaceDirectory.resolve("analysis.json");

        return Files.exists(analysisFile) && analysisState().has(responseProperty);
    }

    @Test
    void anAcceptedFirstAttemptIsStoredAndSucceeds() throws Exception {
        int port = startStubModel("{\"answer\":\"ok\"}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 3);

        assertEquals(0, runAnalysis());

        assertEquals("SUCCESSFUL", stateOf("SingleTask").path("state").asText());
        assertEquals("ok", analysisState().path("singleResult").path("answer").asText());
        assertEquals(1, requestPaths.size(), "an accepted first attempt must not be repeated");
    }

    @Test
    void aRejectedFirstAttemptIsRetriedAndTheSecondAnswerIsStored() throws Exception {
        int port = startStubModel("{\"answer\": 42}", "{\"answer\":\"ok\"}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 3);

        assertEquals(0, runAnalysis());

        assertEquals("SUCCESSFUL", stateOf("SingleTask").path("state").asText());
        assertEquals("ok", analysisState().path("singleResult").path("answer").asText());
        assertEquals(2, requestPaths.size(), "the violation must have been attempted again");
    }

    @Test
    void exhaustedRetriesFailTheTaskAndStoreNothing() throws Exception {
        int port = startStubModel("{\"answer\": 42}", "{\"answer\": 43}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 2);

        assertEquals(0, runAnalysis());

        assertEquals("FAILED", stateOf("SingleTask").path("state").asText());
        assertFalse(hasStoredResult("singleResult"),
                "a parsable payload that violates the schema must not be published");
        assertEquals(2, requestPaths.size(), "the budget of 2 means two attempts in total");
    }

    @Test
    void aBudgetOfOneAttemptDoesNotRetry() throws Exception {
        int port = startStubModel("{\"answer\": 42}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 1);

        assertEquals(0, runAnalysis());

        assertEquals("FAILED", stateOf("SingleTask").path("state").asText());
        assertEquals(1, requestPaths.size(), "a budget of 1 disables the retrying");
    }

    @Test
    void everyAttemptLeavesItsOwnChatLog() throws Exception {
        int port = startStubModel("{\"answer\": 42}", "{\"answer\": 43}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 2);

        runAnalysis();

        Path logs = workspaceDirectory.resolve("logs");

        assertTrue(Files.exists(logs.resolve("SingleTask.log")),
                "the first attempt keeps the plain log name");
        assertTrue(Files.exists(logs.resolve("SingleTask.attempt2.log")),
                "the rejected attempt must leave its transcript behind");
    }

    @Test
    void aFailedTaskDoesNotUnlockItsDependent() throws Exception {
        int port = startStubModel("{\"answer\": 42}");
        writeAnalysis("""
                ---
                id: FirstTask
                name: First Task
                prompt: prompts/single.md
                responseFormat: results/Single.json
                responseProperty: firstResult
                active: true
                tags:
                  - first_done
                ---
                id: DependentTask
                name: Dependent Task
                prompt: prompts/single.md
                responseFormat: results/Single.json
                responseProperty: dependentResult
                active: true
                dependsOn:
                  - first_done
                """);
        writeConfig(port, 1);

        assertEquals(0, runAnalysis());

        assertEquals("FAILED", stateOf("FirstTask").path("state").asText());
        assertNull(stateOf("DependentTask"), "a dependent of a failed task must not be executed");
        assertEquals(1, requestPaths.size(), "only the failing task may have asked the model");
    }

    @Test
    void aFailedTaskIsExecutedAgainOnTheNextRun() throws Exception {
        int port = startStubModel("{\"answer\": 42}", "{\"answer\":\"ok\"}");
        writeAnalysis(ONE_TASK);
        writeConfig(port, 1);

        runAnalysis();

        assertEquals("FAILED", stateOf("SingleTask").path("state").asText());

        // The next run gets a conformant answer and is allowed to redo the task.
        answers.clear();
        answers.add("{\"answer\":\"ok\"}");
        requestPaths.clear();

        runAnalysis();

        assertEquals("SUCCESSFUL", stateOf("SingleTask").path("state").asText());
        assertEquals("ok", analysisState().path("singleResult").path("answer").asText());
        assertEquals(1, requestPaths.size(), "the failed task must have been executed again");
    }

    @Test
    void aRetriableServerErrorIsAttemptedAgain() throws Exception {
        int port = startFailingModel(500);
        writeAnalysis(ONE_TASK);
        writeConfig(port, 3);

        runAnalysis();

        assertEquals("FAILED", stateOf("SingleTask").path("state").asText());
        assertEquals(3, requestPaths.size(),
                "a server error may pass on another attempt, so the budget must be used");
    }

    @Test
    void anAuthenticationFailureIsNotAttemptedAgain() throws Exception {
        int port = startFailingModel(401);
        writeAnalysis(ONE_TASK);
        writeConfig(port, 3);

        runAnalysis();

        assertEquals("FAILED", stateOf("SingleTask").path("state").asText());
        assertEquals(1, requestPaths.size(),
                "a rejected api key cannot be repaired by trying again");
    }

    @Test
    void everyIndexOfALoopTaskIsRetriedOnItsOwn() throws Exception {
        int port = startStubModel("{\"answer\": 42}", "{\"answer\":\"ok\"}");
        writeAnalysis("""
                ---
                id: LoopTask
                name: Loop Task
                prompt: prompts/single.md
                responseFormat: results/Single.json
                responseProperty: loopResult
                active: true
                loopOn: /modules/modules
                tags:
                  - loop_done
                """);
        writeConfig(port, 3);

        // The loop runs over an array an earlier task would have produced.
        Files.writeString(workspaceDirectory.resolve("analysis.json"), """
                {"modules":{"modules":[{"name":"first"},{"name":"second"}]}}
                """);

        assertEquals(0, runAnalysis());

        JsonNode state = stateOf("LoopTask");
        assertEquals("SUCCESSFUL", state.path("state").asText());

        // The first index was rejected once and accepted on its retry, the second one was accepted
        // right away, so both carry a result and the model was asked three times in total.
        JsonNode modules = analysisState().path("modules").path("modules");
        assertEquals("ok", modules.get(0).path("loopResult").path("answer").asText());
        assertEquals("ok", modules.get(1).path("loopResult").path("answer").asText());
        assertEquals(3, requestPaths.size(),
                "one attempt for the first index, one retry, and one attempt for the second index");
    }
}
