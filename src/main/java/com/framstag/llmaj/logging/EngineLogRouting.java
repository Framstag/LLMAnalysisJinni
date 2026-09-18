package com.framstag.llmaj.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes engine log output so that it fits the display mode of the run.
 * <p>
 * The TUI paints its frames on the same stream a console appender writes to, so a log record that
 * reaches the console lands in the middle of a frame. While the TUI owns the terminal, the console
 * appender is therefore detached, the records go to a log file inside the workspace, and the
 * warnings and errors are additionally forwarded to the display, which shows the latest one inside
 * its frame. A run that does not use the TUI keeps the console as the diagnostic channel.
 */
public final class EngineLogRouting {

    /**
     * MDC key the log pattern uses to attribute a record to the task that emitted it.
     */
    public static final String TASK_ID_MDC_KEY = "taskId";

    /**
     * Name of the log file the engine writes when the console is not available.
     */
    public static final String ENGINE_LOG_FILE_NAME = "engine.log";

    private static final Logger logger = LoggerFactory.getLogger(EngineLogRouting.class);

    private static final String LOG_DIRECTORY = "logs";
    private static final String LOG_PATTERN =
            "%d{HH:mm:ss.SSS} [%thread] %-5level [%X{taskId}] %logger{36} - %msg%n";
    private static final String LOG_LINE_APPENDER_NAME = "TUI_LOG_LINE";
    private static final String LOG_FILE_APPENDER_NAME = "ENGINE_LOG_FILE";

    private EngineLogRouting() {
    }

    /**
     * Installs the log routing the display mode of the run needs. Called before the display exists.
     * <p>
     * The console appender stays attached until the display has painted its first frame, see
     * {@link #detachConsoleAfterFirstFrame}. Until then a diagnostic the routing itself produces - an
     * engine log file that cannot be opened - still reaches the console instead of going to a display
     * that does not exist yet.
     *
     * @param useTui          true when the TUI owns the terminal
     * @param workspace       workspace directory, used for the engine log file
     * @param executionTrace  effective execution trace setting
     * @return the sink the display has to register itself with; a sink without a target when the
     * console keeps the log output
     */
    public static ForwardingLogLineSink installForDisplayMode(boolean useTui, Path workspace, boolean executionTrace) {
        ForwardingLogLineSink logLineSink = LogLineSink.forwarding();

        if (useTui) {
            divertToEngineLogFile(workspace, logLineSink);
        } else if (!executionTrace) {
            // The console keeps warnings and errors; the progress of the run is reported by the
            // plain status lines of the simple display.
            rootLogger().setLevel(Level.WARN);
        }

        return logLineSink;
    }

    /**
     * Hands the terminal over to the TUI by detaching the console appenders. Called once the display
     * has painted its first frame and can show the records itself, so that no record is left without a
     * destination in between.
     *
     * @param useTui true when the TUI owns the terminal
     */
    public static void detachConsoleAfterFirstFrame(boolean useTui) {
        if (!useTui) {
            return;
        }

        detachConsoleAppenders(rootLogger());
    }

    /**
     * Path of the engine log file inside a workspace, whether or not the file exists.
     */
    public static Path engineLogFile(Path workspace) {
        return workspace.resolve(LOG_DIRECTORY).resolve(ENGINE_LOG_FILE_NAME);
    }

    private static void divertToEngineLogFile(Path workspace, LogLineSink sink) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = rootLogger();

        // The console is unavailable for the whole run, so the file takes over its role and records
        // The console is unavailable from the first frame on, so the file takes over its role and
        // records every level the console would have shown. The console appender is detached once the
        // frame is painted, see detachConsoleAfterFirstFrame.
        root.setLevel(Level.INFO);

        // Attached before the file appender: a log file that cannot be opened is then still reported,
        // on the console while the console appender is attached and in the frame afterwards.
        LogLineAppender logLineAppender = new LogLineAppender(sink);
        logLineAppender.setName(LOG_LINE_APPENDER_NAME);
        logLineAppender.setContext(context);
        logLineAppender.start();
        root.addAppender(logLineAppender);

        FileAppender<ILoggingEvent> fileAppender = createFileAppender(context, workspace);

        if (fileAppender != null) {
            root.addAppender(fileAppender);
        }
    }

    /**
     * Creates the appender for the engine log file. Returns null when the file cannot be used, so
     * that a workspace the run may not write to does not fail the run.
     */
    private static FileAppender<ILoggingEvent> createFileAppender(LoggerContext context, Path workspace) {
        Path logFile = engineLogFile(workspace);

        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            logger.warn("Cannot create the engine log directory '{}' ({}), engine logs are not written",
                    logFile.getParent(), e.getMessage());
            return null;
        }

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(LOG_PATTERN);
        encoder.start();

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setName(LOG_FILE_APPENDER_NAME);
        fileAppender.setContext(context);
        fileAppender.setFile(logFile.toString());
        fileAppender.setAppend(false);
        fileAppender.setEncoder(encoder);
        fileAppender.start();

        if (!fileAppender.isStarted()) {
            logger.warn("Cannot open the engine log file '{}', engine logs are not written", logFile);
            return null;
        }

        return fileAppender;
    }

    private static void detachConsoleAppenders(ch.qos.logback.classic.Logger root) {
        List<Appender<ILoggingEvent>> consoleAppenders = new ArrayList<>();

        for (var iterator = root.iteratorForAppenders(); iterator.hasNext(); ) {
            Appender<ILoggingEvent> appender = iterator.next();

            if (appender instanceof ConsoleAppender) {
                consoleAppenders.add(appender);
            }
        }

        // Detached, not stopped: the appender is only out of the way for this run, and a caller that
        // has to put the console back must be able to reattach a working appender.
        for (Appender<ILoggingEvent> appender : consoleAppenders) {
            root.detachAppender(appender);
        }
    }

    private static ch.qos.logback.classic.Logger rootLogger() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
