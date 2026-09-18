package com.framstag.llmaj.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the routing of engine log output. The tests change global logback state, so they capture
 * it before and put it back afterwards.
 */
public class EngineLogRoutingTest {

    private static final org.slf4j.Logger testLogger = LoggerFactory.getLogger(EngineLogRoutingTest.class);

    @TempDir
    Path workspace;

    private Level previousLevel;
    private List<Appender<ILoggingEvent>> previousAppenders;

    @BeforeEach
    public void captureLogbackState() {
        Logger root = rootLogger();
        previousLevel = root.getLevel();
        previousAppenders = new ArrayList<>();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            previousAppenders.add(iterator.next());
        }
    }

    @AfterEach
    public void restoreLogbackState() {
        Logger root = rootLogger();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            Appender<ILoggingEvent> appender = iterator.next();

            if (!previousAppenders.contains(appender)) {
                root.detachAppender(appender);
                appender.stop();
            }
        }

        for (Appender<ILoggingEvent> appender : previousAppenders) {
            if (!attached(root).contains(appender)) {
                root.addAppender(appender);
            }
        }

        root.setLevel(previousLevel);
        MDC.clear();
    }

    private static List<Appender<ILoggingEvent>> attached(Logger root) {
        List<Appender<ILoggingEvent>> result = new ArrayList<>();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            result.add(iterator.next());
        }

        return result;
    }

    private static Logger rootLogger() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
    }

    private static boolean hasConsoleAppender() {
        for (var iterator = rootLogger().iteratorForAppenders(); iterator.hasNext(); ) {
            if (iterator.next() instanceof ConsoleAppender) {
                return true;
            }
        }

        return false;
    }

    private static String readEngineLog(Path workspace) throws IOException {
        return Files.readString(EngineLogRouting.engineLogFile(workspace), StandardCharsets.UTF_8);
    }

    /**
     * Collects the records the display would show, so a test can assert what the in-frame line sees.
     */
    private static final class CollectingSink implements LogLineSink {
        private final List<LogLine> lines = new CopyOnWriteArrayList<>();

        @Override
        public void onLogLine(LogLine line) {
            lines.add(line);
        }
    }

    @Test
    public void testConsoleAppenderStaysAttachedUntilTheFirstFrame() {
        assertTrue(hasConsoleAppender(),
                "the test environment must start with the console appender of the logback configuration");

        EngineLogRouting.installForDisplayMode(true, workspace, false);

        assertTrue(hasConsoleAppender(),
                "a diagnostic of the routing itself must still reach the console, because the display"
                        + " that would show it does not exist yet");
    }

    @Test
    public void testTuiBranchDetachesTheConsoleAppenderAfterTheFirstFrame() {
        EngineLogRouting.installForDisplayMode(true, workspace, false);
        EngineLogRouting.detachConsoleAfterFirstFrame(true);

        assertFalse(hasConsoleAppender(),
                "no log record may reach the terminal while the TUI owns it");
    }

    @Test
    public void testRunWithoutTuiKeepsTheConsoleAppender() {
        EngineLogRouting.installForDisplayMode(false, workspace, false);
        EngineLogRouting.detachConsoleAfterFirstFrame(false);

        assertTrue(hasConsoleAppender(),
                "a run without the TUI keeps the console as its diagnostic channel");
        assertEquals(Level.WARN, rootLogger().getLevel(),
                "a run without the execution trace keeps only warnings and errors on the console");
    }

    @Test
    public void testExecutionTraceKeepsTheConsoleAppenderAndLevel() {
        EngineLogRouting.installForDisplayMode(false, workspace, true);

        assertTrue(hasConsoleAppender(), "the execution trace is the verbose console output");
        assertEquals(previousLevel, rootLogger().getLevel(),
                "the execution trace must not change the configured console level");
    }

    @Test
    public void testTuiBranchWritesRecordsToTheEngineLogFile() throws IOException {
        EngineLogRouting.installForDisplayMode(true, workspace, false);
        EngineLogRouting.detachConsoleAfterFirstFrame(true);

        MDC.put(EngineLogRouting.TASK_ID_MDC_KEY, "SomeTask");
        testLogger.warn("engine log routing test record");
        MDC.clear();

        String logFile = readEngineLog(workspace);

        assertTrue(logFile.contains("engine log routing test record"),
                "the diverted record must be written to the engine log file, got:\n" + logFile);
        assertTrue(logFile.contains("WARN"),
                "the record must keep its level, got:\n" + logFile);
        assertTrue(logFile.contains("[SomeTask]"),
                "the record must identify the task it was emitted for, got:\n" + logFile);
    }

    @Test
    public void testEngineLogFileIsOverwrittenPerRun() throws IOException {
        EngineLogRouting.installForDisplayMode(true, workspace, false);
        EngineLogRouting.detachConsoleAfterFirstFrame(true);
        testLogger.warn("record of the first run");

        EngineLogRouting.installForDisplayMode(true, workspace, false);
        EngineLogRouting.detachConsoleAfterFirstFrame(true);
        testLogger.warn("record of the second run");

        String logFile = readEngineLog(workspace);

        assertTrue(logFile.contains("record of the second run"), "the second run must be recorded");
        assertFalse(logFile.contains("record of the first run"),
                "the log file must be overwritten per run, got:\n" + logFile);
    }

    @Test
    public void testWarnAndErrorRecordsReachTheDisplaySink() {
        ForwardingLogLineSink sink = EngineLogRouting.installForDisplayMode(true, workspace, false);
        CollectingSink collecting = new CollectingSink();
        sink.setTarget(collecting);

        MDC.put(EngineLogRouting.TASK_ID_MDC_KEY, "SomeTask");
        testLogger.warn("a warning for the frame");
        MDC.clear();
        testLogger.info("an info record that the frame does not need");

        assertEquals(1, collecting.lines.size(),
                "only the warning belongs on the frame line, got: " + collecting.lines);

        LogLine line = collecting.lines.getFirst();

        assertEquals("WARN", line.level(), "the sink must receive the level of the record");
        assertEquals(EngineLogRoutingTest.class.getName(), line.loggerName(),
                "the sink must receive the logger that emitted the record");
        assertEquals("SomeTask", line.taskId(), "the sink must receive the task the record belongs to");
        assertEquals("a warning for the frame", line.message(), "the sink must receive the formatted message");
    }

    @Test
    public void testUnusableLogFileIsReportedAndDoesNotFailTheRun() throws IOException {
        Path unwritableWorkspace = workspace.resolve("unwritable");
        Files.createDirectories(unwritableWorkspace);
        Files.writeString(unwritableWorkspace.resolve("logs"), "a regular file where the directory should be");

        ListAppender<ILoggingEvent> routingRecords = new ListAppender<>();
        routingRecords.start();
        Logger routingLogger = (Logger) LoggerFactory.getLogger(EngineLogRouting.class);
        routingLogger.addAppender(routingRecords);

        try {
            ForwardingLogLineSink sink = EngineLogRouting.installForDisplayMode(true, unwritableWorkspace, false);

            // The diagnostic exists: while the console appender is still attached it is not lost, even
            // though the display that would show it on its reserved line does not exist yet.
            assertFalse(routingRecords.list.isEmpty(),
                    "a log file that cannot be created must be reported, not silently skipped");
            assertTrue(routingRecords.list.getFirst().getFormattedMessage().contains("logs"),
                    "the diagnostic must name the directory it could not use, got: "
                            + routingRecords.list.getFirst().getFormattedMessage());

            CollectingSink collecting = new CollectingSink();
            sink.setTarget(collecting);
            EngineLogRouting.detachConsoleAfterFirstFrame(true);

            testLogger.warn("the run continues without a log file");

            assertEquals(1, collecting.lines.size(),
                    "the in-frame line must still work when the log file cannot be written, got: "
                            + collecting.lines);
            assertFalse(Files.exists(EngineLogRouting.engineLogFile(unwritableWorkspace)),
                    "no engine log file can exist in a workspace that cannot hold it");
        } finally {
            routingLogger.detachAppender(routingRecords);
            routingRecords.stop();
        }
    }
}
