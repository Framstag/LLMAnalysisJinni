package com.framstag.llmaj.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
 * Drives real analyses against a stub model server, so the loop task, the dispatch rule and the task
 * statuses are verified end to end.
 * <p>
 * The Maven run of 2026-10-06 executed six loop tasks over the same array; five of them were refused
 * with "Loop already started" and re-dispatched until the log had 34 million records. These tests
 * cover the two defects that produced it: a loop task refused because another loop task is running,
 * and a task that ends without a status change being dispatched again.
 */
public class AnalyseCmdLoopTaskTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String SCHEMA = """
            {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
            """;

    private static final String TWO_MODULES = """
            {"modules":{"modules":[{"name":"first"},{"name":"second"}]}}
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

    private Level previousRootLevel;
    private List<Appender<ILoggingEvent>> previousRootAppenders;

    private record Run(int exitCode, List<String> records) {
    }

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

    /**
     * Runs the analysis with the engine's warnings and errors collected, so a test can assert on what
     * the run reported without reading a log file.
     */
    private Run runAnalysis() {
        Logger root = rootLogger();
        ListAppender<ILoggingEvent> records = new ListAppender<>();
        records.start();
        root.addAppender(records);

        try {
            AnalyseCmd analyseCmd = new AnalyseCmd();
            new CommandLine(analyseCmd).parseArgs(workspaceDirectory.toString());

            PrintStream originalOut = System.out;
            System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

            int exitCode;

            try {
                exitCode = analyseCmd.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                System.setOut(originalOut);
            }

            List<String> messages = new ArrayList<>();

            for (ILoggingEvent event : records.list) {
                messages.add(event.getFormattedMessage());
            }

            return new Run(exitCode, messages);
        } finally {
            root.detachAppender(records);
            records.stop();
        }
    }

    private long countRecordsContaining(List<String> records, String text) {
        return records.stream().filter(message -> message.contains(text)).count();
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

    private JsonNode analysisState() throws IOException {
        return mapper.readTree(workspaceDirectory.resolve("analysis.json").toFile());
    }

    private static final String TWO_LOOP_TASKS = """
            ---
            id: FirstLoopTask
            name: First Loop Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: firstResult
            active: true
            loopOn: /modules/modules
            tags:
              - first_loop_done
            ---
            id: SecondLoopTask
            name: Second Loop Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: secondResult
            active: true
            loopOn: /modules/modules
            tags:
              - second_loop_done
            """;

    @Test
    void twoLoopTasksOverTheSameArrayBothExecute() throws Exception {
        int port = startStubModel("{\"answer\":\"ok\"}");
        writeAnalysis(TWO_LOOP_TASKS);
        writeConfig(port, 1);
        writeModules(TWO_MODULES);

        Run run = runAnalysis();

        assertEquals(0, run.exitCode(), "the run must complete");
        assertEquals("SUCCESSFUL", stateOf("FirstLoopTask").path("state").asText(),
                "a loop task must not be refused because another loop task is executing");
        assertEquals("SUCCESSFUL", stateOf("SecondLoopTask").path("state").asText());
        assertEquals(0, countRecordsContaining(run.records(), "already started"),
                "no execution may be refused because a loop is running, got: " + run.records());

        JsonNode entries = analysisState().path("modules").path("modules");

        assertEquals("ok", entries.get(0).path("firstResult").path("answer").asText(),
                "the first loop task must store its result per index");
        assertEquals("ok", entries.get(0).path("secondResult").path("answer").asText(),
                "the second loop task must store its result in the same entry");
        assertEquals(4, requestPaths.size(),
                "two loop tasks with two indices each ask the model four times");
    }

    private static final String BROKEN_LOOP_TASK_WITH_DEPENDENT = """
            ---
            id: BrokenLoopTask
            name: Broken Loop Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: brokenResult
            active: true
            loopOn: /modules/doesNotExist
            tags:
              - broken_loop_done
            ---
            id: DependentTask
            name: Dependent Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: dependentResult
            active: true
            dependsOn:
              - broken_loop_done
            """;

    @Test
    void anUnstartableLoopTaskIsMarkedFailedAndReportedOnce() throws Exception {
        int port = startStubModel("{\"answer\":\"ok\"}");
        writeAnalysis(BROKEN_LOOP_TASK_WITH_DEPENDENT);
        writeConfig(port, 3);
        writeModules(TWO_MODULES);

        Run run = runAnalysis();

        assertEquals(0, run.exitCode(), "an unstartable task must not abort the run");
        assertEquals("FAILED", stateOf("BrokenLoopTask").path("state").asText(),
                "a task whose execution cannot start must not stay pending");
        assertNull(stateOf("DependentTask"),
                "a dependent of an unstartable task must not be executed");
        assertEquals(0, requestPaths.size(), "no index of a broken loop target may ask the model");

        assertEquals(1, countRecordsContaining(run.records(), "Cannot loop on '/modules/doesNotExist'"),
                "the broken target must be reported exactly once, got: " + run.records());
        assertEquals(1, countRecordsContaining(run.records(), "Configuration error, aborting task BrokenLoopTask!"),
                "the aborted start must be reported exactly once and not re-dispatched, got: "
                        + run.records());

        // A task marked failed is executed again on the next run, so the abort is reported again
        // exactly once - it is dispatched in the new run, and still not twice.
        Run secondRun = runAnalysis();

        assertEquals("FAILED", stateOf("BrokenLoopTask").path("state").asText());
        assertEquals(1, countRecordsContaining(secondRun.records(), "Configuration error, aborting task BrokenLoopTask!"),
                "the next run must execute the failed task again, exactly once, got: "
                        + secondRun.records());
        assertEquals(0, countRecordsContaining(secondRun.records(), "already started"),
                "the rerun must not refuse a loop task because of a loop cursor, got: "
                        + secondRun.records());
    }

    private static final String FAILING_TASK_WITH_BLOCKED_DEPENDENT = """
            ---
            id: FailingTask
            name: Failing Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: failingResult
            active: true
            tags:
              - failing_done
            ---
            id: BlockedDependentTask
            name: Blocked Dependent Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: blockedResult
            active: true
            dependsOn:
              - failing_done
            """;

    @Test
    void aRunNamesTheTasksItCannotDispatch() throws Exception {
        int port = startStubModel("{\"answer\": 42}");
        writeAnalysis(FAILING_TASK_WITH_BLOCKED_DEPENDENT);
        writeConfig(port, 1);

        Run run = runAnalysis();

        assertEquals(0, run.exitCode(), "a blocked task must not keep the run alive");
        assertEquals("FAILED", stateOf("FailingTask").path("state").asText());
        assertNull(stateOf("BlockedDependentTask"),
                "the dependent of a failed task must not run");
        assertEquals(1, requestPaths.size(), "only the failing task may ask the model");
        assertEquals(1, countRecordsContaining(run.records(), "not dispatched: [BlockedDependentTask]"),
                "the run must name the task it could not dispatch, got: " + run.records());
    }

    private static final String CHAIN_OF_THREE = """
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
            id: SecondTask
            name: Second Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: secondResult
            active: true
            dependsOn:
              - first_done
            tags:
              - second_done
            ---
            id: ThirdTask
            name: Third Task
            prompt: prompts/single.md
            responseFormat: results/Single.json
            responseProperty: thirdResult
            active: true
            dependsOn:
              - second_done
            """;

    @Test
    void aCompletionDispatchesTheTaskItUnlocks() throws Exception {
        int port = startStubModel("{\"answer\":\"ok\"}");
        writeAnalysis(CHAIN_OF_THREE);
        writeConfig(port, 1);

        Run run = runAnalysis();

        assertEquals(0, run.exitCode());
        assertEquals("SUCCESSFUL", stateOf("FirstTask").path("state").asText());
        assertEquals("SUCCESSFUL", stateOf("SecondTask").path("state").asText());
        assertEquals("SUCCESSFUL", stateOf("ThirdTask").path("state").asText());
        assertEquals(3, requestPaths.size(), "each task of the chain must be executed once");
    }

    @Test
    void aRerunSkipsTheLoopIndicesAlreadySuccessful() throws Exception {
        int port = startStubModel("{\"answer\":\"ok\"}", "{\"answer\": 42}");
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
        writeConfig(port, 1);
        writeModules(TWO_MODULES);

        runAnalysis();

        assertEquals("FAILED", stateOf("LoopTask").path("state").asText(),
                "an index that is rejected in every attempt fails the task");
        JsonNode afterFirstRun = analysisState().path("modules").path("modules");
        assertEquals("ok", afterFirstRun.get(0).path("loopResult").path("answer").asText(),
                "the successful index must keep its result");
        assertTrue(afterFirstRun.get(1).path("loopResult").isMissingNode(),
                "the rejected index must store nothing");

        // The next run gets a conformant answer; only the index that has no result may ask for it.
        answers.clear();
        answers.add("{\"answer\":\"ok\"}");
        requestPaths.clear();

        runAnalysis();

        assertEquals("SUCCESSFUL", stateOf("LoopTask").path("state").asText());
        assertEquals(1, requestPaths.size(),
                "a rerun must ask for the index without a result only");
        JsonNode afterSecondRun = analysisState().path("modules").path("modules");
        assertEquals("ok", afterSecondRun.get(1).path("loopResult").path("answer").asText(),
                "the remaining index must be stored");
    }
}
