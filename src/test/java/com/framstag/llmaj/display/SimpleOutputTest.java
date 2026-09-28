package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The piped/CI output prints one line per retry, so a step that is attempted again is visible
 * where the TUI cannot be.
 */
public class SimpleOutputTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    private List<String> lines() {
        return captured.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void oneLineIsPrintedPerRetry() {
        SimpleOutput output = new SimpleOutput(new Config());

        output.onRetry("FirstTask", null, 1, 3, "schema violation: $.answer is not a string");
        output.onRetry("FirstTask", null, 2, 3, "no JSON payload in the response");

        List<String> retryLines = lines().stream()
                .filter(line -> line.contains("FirstTask"))
                .toList();

        assertEquals(2, retryLines.size(), "one retry must produce exactly one line, got: " + lines());
        assertTrue(retryLines.getFirst().contains("attempt 1/3"),
                "the line must name the attempt, got: " + retryLines.getFirst());
        assertTrue(retryLines.getFirst().contains("$.answer is not a string"),
                "the line must carry the reason, got: " + retryLines.getFirst());
        assertTrue(retryLines.getFirst().contains("retrying"), "the line must say that it retries");
    }

    @Test
    void aRetriedLoopIndexNamesItsIndex() {
        SimpleOutput output = new SimpleOutput(new Config());

        output.onRetry("LoopTask", 4, 1, 3, "the located JSON payload does not parse");

        assertTrue(lines().stream().anyMatch(line -> line.contains("LoopTask[4]")
                        && line.contains("attempt 1/3")
                        && line.contains("does not parse")),
                "a loop index must be identifiable in the retry line, got: " + lines());
    }

    @Test
    void aFinishedStepWithoutARetryPrintsNoRetryLine() {
        SimpleOutput output = new SimpleOutput(new Config());

        output.onTaskStart("FirstTask", "First Task");
        output.onTaskComplete("FirstTask", "First Task");

        assertTrue(lines().stream().noneMatch(line -> line.contains("retrying")),
                "a step that was not retried must not print a retry line, got: " + lines());
    }
}
