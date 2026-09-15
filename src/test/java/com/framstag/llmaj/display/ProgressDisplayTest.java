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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class ProgressDisplayTest {

    private static final String ESCAPE = "\u001b";
    private static final Pattern CURSOR_UP = Pattern.compile("\u001b\\[\\d+A");

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

    private List<TaskDefinition> tasks(String... taskNames) throws IOException {
        StringBuilder yaml = new StringBuilder();

        for (String taskName : taskNames) {
            yaml.append("---\n")
                    .append("id: ").append(taskName.toLowerCase().replace(' ', '-')).append('\n')
                    .append("name: ").append(taskName).append('\n')
                    .append("responseFormat: results/").append(taskName.replace(" ", "")).append(".json\n");
        }

        Path taskFile = tempDirectory.resolve("tasks.yaml");
        Files.writeString(taskFile, yaml.toString());

        return TaskDefinition.loadTasks(taskFile);
    }

    private static void awaitOutputGrowth(ByteArrayOutputStream output, int previousSize, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < deadline) {
            if (output.size() > previousSize) {
                return;
            }

            Thread.sleep(50);
        }

        fail("Expected the display to write another frame after " + timeoutMillis + "ms");
    }

    @Test
    public void testFirstFrameDoesNotMoveTheCursor() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        try {
            display.addTasks(tasks("First Task"));

            String firstFrame = output.toString(StandardCharsets.UTF_8);

            assertTrue(firstFrame.contains("First Task"));
            assertFalse(CURSOR_UP.matcher(firstFrame).find(),
                    "the first frame has nothing on screen to move the cursor back over");
        } finally {
            display.close();
        }
    }

    @Test
    public void testRepaintMovesCursorBackOverThePreviousFrame() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        try {
            display.addTasks(tasks("First Task"));

            output.reset();
            display.addTasks(tasks("Second Task"));

            String repaint = output.toString(StandardCharsets.UTF_8);

            assertTrue(CURSOR_UP.matcher(repaint).find(),
                    "a repaint must move the cursor back to the start of the frame on screen");
        } finally {
            display.close();
        }
    }

    @Test
    public void testUnchangedFrameIsNotWrittenAgain() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        try {
            display.addTasks(tasks("First Task"));

            int sizeOfFirstPaint = output.size();

            // Longer than one render tick, but without anything the user could see changing.
            Thread.sleep(550);

            assertEquals(sizeOfFirstPaint, output.size(),
                    "an unchanged frame must not be written to the terminal again");

            // Once the displayed elapsed time changes, the frame is written again.
            awaitOutputGrowth(output, sizeOfFirstPaint, 3000);
        } finally {
            display.close();
        }
    }

    @Test
    public void testTerminalWithoutAnsiSupportGetsNoEscapeSequences() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TerminalSupport terminalSupport = TerminalSupport.of(new FixedSizeTerminal(output));

        assertFalse(terminalSupport.stdoutIsTerminal());
        assertFalse(terminalSupport.ansiSupported());

        ProgressDisplay display = new ProgressDisplay(config(),
                terminalSupport.terminal(),
                terminalSupport.ansiSupported(),
                terminalSupport.unicodeSupported());

        try {
            display.addTasks(tasks("First Task"));
            display.onTaskStart("first-task", "First Task");
        } finally {
            display.close();
        }

        String rendered = output.toString(StandardCharsets.UTF_8);

        assertTrue(rendered.contains("First Task"));
        assertFalse(rendered.contains(ESCAPE),
                "no escape sequence may be written to a terminal without ANSI support");
    }
}
