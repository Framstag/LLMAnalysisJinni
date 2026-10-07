package com.framstag.llmaj.tools.file;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the diagnostics of the file tool. The Maven run of 2026-10-06 logged a read of a file the
 * model had guessed ('README' instead of 'README.md') as an ERROR with a stack trace, although the
 * model could correct the path itself and did.
 */
public class FileIOToolTest {

    private final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path projectDirectory;

    private ListAppender<ILoggingEvent> records;
    private FileIOTool fileIOTool;

    @BeforeEach
    void setUp() {
        records = new ListAppender<>();
        records.start();
        ((Logger) LoggerFactory.getLogger(FileIOTool.class)).addAppender(records);

        fileIOTool = new FileIOTool(new AnalysisContext(projectDirectory, projectDirectory,
                Map.of(), mapper.createObjectNode()));
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(FileIOTool.class)).detachAppender(records);
        records.stop();
    }

    private List<ILoggingEvent> recordsAbove(Level level) {
        return records.list.stream()
                .filter(record -> record.getLevel().isGreaterOrEqual(level))
                .toList();
    }

    @Test
    void existingFileIsReturned() throws Exception {
        Files.writeString(projectDirectory.resolve("README.md"), "# A readme");

        assertEquals("# A readme", fileIOTool.readFile("README.md"));
        assertTrue(recordsAbove(Level.WARN).isEmpty(),
                "reading a file that exists must not be reported as a problem");
    }

    @Test
    void missingFileIsAToolResultAndNotAnError() {
        String result = fileIOTool.readFile("README");

        assertTrue(result.contains("README"),
                "the tool result must name the file it could not read, got: " + result);
        assertTrue(result.contains("cannot be read"),
                "the tool result must name the condition, got: " + result);

        assertTrue(recordsAbove(Level.ERROR).isEmpty(),
                "a path the model can correct must not be reported as an error, got: "
                        + records.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        assertTrue(records.list.stream().noneMatch(record -> record.getThrowableProxy() != null),
                "a condition the model can correct must not write a stack trace");

        assertEquals(1, recordsAbove(Level.WARN).size(),
                "the condition must be reported once, got: "
                        + records.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test
    void repeatedConditionIsReportedOnce() {
        fileIOTool.readFile("README");
        fileIOTool.readFile("README");
        fileIOTool.readFile("README");

        assertEquals(1, recordsAbove(Level.WARN).size(),
                "a repetition of the same condition must not be reported again above DEBUG");
        assertEquals(0, recordsAbove(Level.ERROR).size(),
                "no repetition may be reported as an error");
    }

    @Test
    void pathOutsideTheProjectIsReportedOnceAndNamedInTheResult() {
        String result = fileIOTool.readFile("../outside.txt");

        assertTrue(result.contains("../outside.txt"),
                "the tool result must name the path it refused, got: " + result);
        assertEquals(1, recordsAbove(Level.WARN).size(),
                "the refused path must be reported once");
        assertTrue(recordsAbove(Level.ERROR).isEmpty(),
                "a refused path is a condition the model can correct, not an error");
    }

    @Test
    void everyDistinctConditionIsReported() {
        fileIOTool.readFile("first-missing.txt");
        fileIOTool.readFile("second-missing.txt");

        assertEquals(2, recordsAbove(Level.WARN).size(),
                "two different conditions are two records");
    }

    /**
     * Only a condition the model caused is softened into a tool result. A defect of the tool itself is
     * not swallowed, so it still reaches the engine's error reporting.
     */
    @Test
    void aDefectOfTheToolIsNotTurnedIntoAToolResult() {
        assertThrows(RuntimeException.class, () -> fileIOTool.readFile(null),
                "a failure of the tool itself must not be answered as if the model could correct it");
    }
}
