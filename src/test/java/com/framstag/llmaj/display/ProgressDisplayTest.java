package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.logging.LogLine;
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

    private static LogLine warn(String message) {
        return new LogLine("WARN", "test.logger", "first-task", message);
    }

    private static long lineCount(String rendered) {
        return rendered.lines().count();
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
    public void testWarningIsShownOnTheReservedLine() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        try {
            display.addTasks(tasks("First Task"));

            output.reset();
            display.onLogLine(warn("the response could not be parsed"));

            awaitOutputGrowth(output, 0, 3000);

            String frame = output.toString(StandardCharsets.UTF_8);

            assertTrue(frame.contains("! [first-task] the response could not be parsed"),
                    "the latest warning must be shown on the reserved line, got:\n" + frame);
        } finally {
            display.close();
        }
    }

    @Test
    public void testReservedLineIsEmptyWithoutARecord() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        try {
            display.addTasks(tasks("First Task"));

            String firstFrame = output.toString(StandardCharsets.UTF_8);

            assertFalse(firstFrame.contains("!"),
                    "a frame without a record must not show a warning, got:\n" + firstFrame);
            assertTrue(firstFrame.contains("Token: IN"),
                    "the frame must still reserve the line and keep its footer, got:\n" + firstFrame);
        } finally {
            display.close();
        }
    }

    @Test
    public void testLongWarningIsTruncatedToTheFrameWidth() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);
        String longMessage = "x".repeat(500);

        try {
            display.addTasks(tasks("First Task"));

            output.reset();
            display.onLogLine(warn(longMessage));

            awaitOutputGrowth(output, 0, 3000);

            String frame = output.toString(StandardCharsets.UTF_8);

            assertFalse(frame.contains(longMessage),
                    "a warning longer than the frame width must be truncated, got a frame of "
                            + lineCount(frame) + " lines");
            assertTrue(frame.contains("..."), "a truncated warning must say that it was truncated");
        } finally {
            display.close();
        }
    }

    @Test
    public void testNewerWarningReplacesTheOlderOne() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        try {
            display.addTasks(tasks("First Task"));

            output.reset();
            display.onLogLine(warn("the first warning"));
            awaitOutputGrowth(output, 0, 3000);

            output.reset();
            display.onLogLine(warn("the second warning"));

            awaitOutputGrowth(output, 0, 3000);

            String frame = output.toString(StandardCharsets.UTF_8);

            assertTrue(frame.contains("the second warning"),
                    "the newer warning must be shown, got:\n" + frame);
            assertFalse(frame.contains("the first warning"),
                    "the line shows the latest record only, got:\n" + frame);
        } finally {
            display.close();
        }
    }

    @Test
    public void testWarningKeepsTheFrameLineCountStable() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        try {
            display.addTasks(tasks("First Task"));

            long linesWithoutRecord = lineCount(output.toString(StandardCharsets.UTF_8));

            output.reset();
            display.onLogLine(warn("a warning that adds no line"));

            awaitOutputGrowth(output, 0, 3000);

            String frame = output.toString(StandardCharsets.UTF_8);

            assertEquals(linesWithoutRecord, lineCount(frame),
                    "showing a warning must not change the number of lines the frame occupies");
            assertTrue(CURSOR_UP.matcher(frame).find(),
                    "the repaint after a warning must move the cursor back over the previous frame");
            assertTrue(frame.contains("\u001b[" + linesWithoutRecord + "A"),
                    "the repaint must move back over exactly the lines the previous frame occupied, got:\n"
                            + frame);
        } finally {
            display.close();
        }
    }

    @Test
    public void testWarningArrivingJustBeforeCloseIsStillShown() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        display.addTasks(tasks("First Task"));
        display.onLogLine(warn("the earlier warning"));

        output.reset();
        display.onLogLine(warn("the warning of the last tick"));
        display.close();

        String rendered = output.toString(StandardCharsets.UTF_8);

        assertTrue(rendered.contains("! [first-task] the warning of the last tick"),
                "a record that arrives after the last rendered frame must still be shown, got:\n"
                        + rendered);
        assertTrue(rendered.indexOf("the warning of the last tick")
                        < rendered.indexOf("=== Analysis Complete ==="),
                "the last frame must be painted before the summary, got:\n" + rendered);
    }

    @Test
    public void testBurstOfWarningsDoesNotPaintAFrameEach() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProgressDisplay display = new ProgressDisplay(config(), new FixedSizeTerminal(output), true, true);

        display.addTasks(tasks("First Task"));
        display.onLogLine(warn("the warning that starts the burst"));

        output.reset();

        for (int i = 0; i < 10; i++) {
            display.onLogLine(warn("burst record " + i));
        }
        display.close();

        String rendered = output.toString(StandardCharsets.UTF_8);
        int repaints = CURSOR_UP.matcher(rendered).results().toList().size();

        assertTrue(rendered.contains("! [first-task] burst record 9"),
                "the newest record of the burst must be shown, got:\n" + rendered);
        assertTrue(repaints < 10,
                "10 records must not paint 10 frames, the burst must be coalesced, painted frames: "
                        + repaints);
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
