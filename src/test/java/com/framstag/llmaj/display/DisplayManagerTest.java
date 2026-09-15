package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.tasks.TaskDefinition;
import org.jline.terminal.Size;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
                Set.of());

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
    public void testSuccessfulTaskIsRenderedWithoutFailureMarker() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DisplayManager displayManager = new DisplayManager(config(),
                DisplayDecision.decide(false, true),
                TerminalSupport.of(new FixedSizeTerminal(output)),
                tasks(),
                Set.of());

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
}
