package com.framstag.llmaj.tools.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the diagnostics of the CSV report writer. They must go through the engine logging path, so
 * that the engine log routing decides where they end up instead of a direct write to the error
 * stream that would land in the middle of a TUI frame.
 */
public class CsvReportWriterTest {

    @TempDir
    Path workspace;

    private ListAppender<ILoggingEvent> capturedRecords;
    private PrintStream originalErrorStream;
    private ByteArrayOutputStream capturedErrorStream;

    @BeforeEach
    public void captureDiagnostics() {
        capturedRecords = new ListAppender<>();
        capturedRecords.start();
        loggerOfTheWriter().addAppender(capturedRecords);

        originalErrorStream = System.err;
        capturedErrorStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErrorStream, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    public void releaseDiagnostics() {
        System.setErr(originalErrorStream);
        loggerOfTheWriter().detachAppender(capturedRecords);
        capturedRecords.stop();
    }

    private static Logger loggerOfTheWriter() {
        return (Logger) LoggerFactory.getLogger(CsvReportWriter.class);
    }

    private String writtenErrorMessageStream() {
        return capturedErrorStream.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void testFailingCsvWriteIsReportedThroughTheLogger() throws IOException {
        // A regular file where the directory of the report should be, so creating the directory fails.
        Files.writeString(workspace.resolve("ReportDirectory"), "not a directory");

        CsvReportWriter.writeCsv(workspace, "ReportDirectory/report.csv",
                new String[]{"Key", "Count"}, List.<String[]>of(new String[]{"a", "1"}));

        assertEquals("", writtenErrorMessageStream(),
                "a diagnostic must not be written directly to the error stream, it would land in a frame");

        assertFalse(capturedRecords.list.isEmpty(),
                "the failed report write must be reported through the logger");

        String message = capturedRecords.list.getFirst().getFormattedMessage();

        assertTrue(message.contains("ReportDirectory/report.csv"),
                "the report must be named in the diagnostic, got: " + message);
        assertEquals("ERROR", capturedRecords.list.getFirst().getLevel().toString(),
                "a report that cannot be written is an error, got: " + capturedRecords.list.getFirst());
    }

    @Test
    public void testSuccessfulCsvWriteStaysSilent() {
        CsvReportWriter.writeCsv(workspace, "reports/report.csv",
                new String[]{"Key", "Count"}, List.<String[]>of(new String[]{"a", "1"}));

        assertTrue(capturedRecords.list.isEmpty(),
                "a report that was written needs no diagnostic, got: " + capturedRecords.list);
        assertEquals("", writtenErrorMessageStream(), "a written report needs no error output");
        assertTrue(Files.exists(workspace.resolve("reports/report.csv")),
                "the report must be written where it was asked for");
    }
}
