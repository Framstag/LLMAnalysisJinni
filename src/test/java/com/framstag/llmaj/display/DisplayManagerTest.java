package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.logging.LogLineSink;
import com.framstag.llmaj.tasks.TaskDefinition;
import org.jline.terminal.Size;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DisplayManagerTest {

    /**
     * Terminal with a fixed size, so the rendering tests do not depend on the size the terminal
     * discovery happens to find in the environment they run in.
     */
    private static final class FixedSizeTerminal extends DumbTerminal {

        FixedSizeTerminal(OutputStream output) throws IOException {
            super("test-terminal", "UTF-8", new ByteArrayInputStream(new byte[0]), output, StandardCharsets.UTF_8);
        }

        @Override
        public Size getSize() {
            return new Size(120, 40);
        }
    }

    @TempDir
    Path tempDirectory;

    private static Config config() {
        Config config = new Config();
        config.setModelName("test-model");
        config.setAnalysisDirectory(Path.of("analysis/software-architecture"));

        return config;
    }

    private List<TaskDefinition> tasks() throws IOException {
        Path taskFile = tempDirectory.resolve("tasks.yaml");
        Files.writeString(taskFile, """
                ---
                id: first-task
                name: First Task
                responseFormat: results/First.json
                ---
                id: second-task
                name: Second Task
                responseFormat: results/Second.json
                """);

        return TaskDefinition.loadTasks(taskFile);
    }

    private static void awaitOutput(ByteArrayOutputStream output, String expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {
            if (output.toString(StandardCharsets.UTF_8).contains(expected)) {
                return;
            }

            Thread.sleep(50);
        }

        fail("Expected the display to contain '" + expected + "', got: "
                + output.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void testFailedTaskIsRenderedAsFailure() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DisplayManager displayManager = new DisplayManager(config(),
                DisplayDecision.decide(false, true),
                TerminalSupport.of(new FixedSizeTerminal(output)),
                tasks(),
                Set.of(),
                LogLineSink.forwarding());

        try {
            displayManager.onTaskStart("first-task", "First Task");
            displayManager.onTaskError("first-task", "First Task", "task broke");

            awaitOutput(output, "task broke", 3000);

            String rendered = output.toString(StandardCharsets.UTF_8);

            assertTrue(rendered.contains("\u2717"), "a failed task must be rendered as failed");
        } finally {
            displayManager.close();
        }
    }

    @Test
    public void testAlreadySuccessfulTaskIsSuccessfulInTheFirstFrame() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DisplayManager displayManager = new DisplayManager(config(),
                DisplayDecision.decide(false, true),
                TerminalSupport.of(new FixedSizeTerminal(output)),
                tasks(),
                Set.of("first-task"),
                LogLineSink.forwarding());

        try {
            String firstFrame = output.toString(StandardCharsets.UTF_8);

            assertTrue(firstFrame.contains("First Task"),
                    "the first frame must list the tasks, got:\n" + firstFrame);
            assertTrue(firstFrame.lines().anyMatch(line -> line.contains("First Task") && line.contains("\u2713")),
                    "a task that was already successful must be successful in the first frame, got:\n"
                            + firstFrame);
            assertFalse(firstFrame.lines().anyMatch(line -> line.contains("First Task") && line.contains("\u2026")),
                    "a task that was already successful must not be pending in the first frame, got:\n"
                            + firstFrame);
            assertTrue(firstFrame.lines().anyMatch(line -> line.contains("Second Task") && line.contains("\u2026")),
                    "a task that was not executed yet must be pending in the first frame, got:\n" + firstFrame);
        } finally {
            displayManager.close();
        }
    }

    @Test
    public void testSuccessfulTaskIsRenderedWithoutFailureMarker() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DisplayManager displayManager = new DisplayManager(config(),
                DisplayDecision.decide(false, true),
                TerminalSupport.of(new FixedSizeTerminal(output)),
                tasks(),
                Set.of(),
                LogLineSink.forwarding());

        try {
            displayManager.onTaskStart("first-task", "First Task");
            displayManager.onTaskComplete("first-task", "First Task");

            awaitOutput(output, "First Task", 3000);
        } finally {
            displayManager.close();
        }

        String rendered = output.toString(StandardCharsets.UTF_8);

        assertTrue(rendered.contains("\u2713"), "a successful task must be rendered as successful");
        assertFalse(rendered.contains("\u2717"), "a successful task must not be rendered as failed");
    }

    @Test
    public void testRetryReachesTheTuiInTuiMode() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DisplayManager displayManager = new DisplayManager(config(),
                DisplayDecision.decide(false, true),
                TerminalSupport.of(new FixedSizeTerminal(output)),
                tasks(),
                Set.of(),
                LogLineSink.forwarding());

        try {
            displayManager.onTaskStart("first-task", "First Task");
            displayManager.getCallback().onRetry("first-task", null, 1, 3, "no JSON payload in the response");

            awaitOutput(output, "attempt 2/3", 3000);
        } finally {
            displayManager.close();
        }
    }

    @Test
    public void testRetryReachesThePipedOutputInSimpleMode() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        DisplayManager displayManager = new DisplayManager(config(),
                DisplayDecision.decide(false, false),
                TerminalSupport.none(),
                List.of(),
                Set.of(),
                LogLineSink.forwarding());

        try {
            displayManager.getCallback().onRetry("first-task", null, 1, 3,
                    "schema violation: $.answer is not a string");
        } finally {
            displayManager.close();
            System.setOut(originalOut);
        }

        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("attempt 1/3"),
                "the piped output must print the retry, got: " + captured);
    }

    @Test
    public void testRetryIsSilentInExecutionTraceMode() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

        DisplayManager displayManager = new DisplayManager(config(),
                DisplayDecision.decide(true, true),
                TerminalSupport.none(),
                List.of(),
                Set.of(),
                LogLineSink.forwarding());

        try {
            displayManager.getCallback().onRetry("first-task", null, 1, 3, "no response text");
        } finally {
            displayManager.close();
            System.setOut(originalOut);
        }

        assertTrue(captured.toString(StandardCharsets.UTF_8).isEmpty(),
                "the execution trace owns the console, the display must add nothing, got: " + captured);
    }
}
