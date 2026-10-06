package com.framstag.llmaj.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.Duration;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

    /**
     * How often the rolling appender may check the size of the engine log file. logback checks the
     * size at most once per interval by default, which would let a burst of records - the case this
     * bound exists for - grow one file far beyond its bound before the first check. Zero means every
     * record is checked, which is one file length per record.
     */
    private static final Duration LOG_SIZE_CHECK_INTERVAL = Duration.buildByMilliseconds(0);

    /**
     * Bound of the engine log of one workspace: no run may grow it beyond
     * {@code maxFileSize * (maxRolledFiles + 1)}, whatever the code logs. The value has to hold the
     * records a diagnosis needs (a run that failed, plus the records before it) without letting a
     * component that repeats a record fill the disk.
     */
    static final EngineLogBounds DEFAULT_LOG_BOUNDS =
            new EngineLogBounds(FileSize.valueOf("32MB"), 2);

    /**
     * Size bound of the engine log files of a workspace.
     *
     * @param maxFileSize    maximum size of one engine log file
     * @param maxRolledFiles number of rolled files kept beside the current one
     */
    record EngineLogBounds(FileSize maxFileSize, int maxRolledFiles) {
    }

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
        return installForDisplayMode(useTui, workspace, executionTrace, DEFAULT_LOG_BOUNDS);
    }

    /**
     * Installs the routing with an explicit engine log bound. Package private for the tests that have
     * to exhaust a bound without writing the production amount of log data.
     */
    static ForwardingLogLineSink installForDisplayMode(boolean useTui,
                                                       Path workspace,
                                                       boolean executionTrace,
                                                       EngineLogBounds bounds) {
        ForwardingLogLineSink logLineSink = LogLineSink.forwarding();

        if (useTui) {
            divertToEngineLogFile(workspace, logLineSink, bounds);
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

    private static void divertToEngineLogFile(Path workspace, LogLineSink sink, EngineLogBounds bounds) {
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

        FileAppender<ILoggingEvent> fileAppender = createFileAppender(context, workspace, bounds);

        if (fileAppender != null) {
            root.addAppender(fileAppender);
        }
    }

    /**
     * Creates the appender for the engine log file. Returns null when the file cannot be used, so
     * that a workspace the run may not write to does not fail the run. The appender rolls the file at
     * its size bound and keeps a fixed number of rolled files, so the engine log files of a workspace
     * stay within the bound of the run.
     */
    private static FileAppender<ILoggingEvent> createFileAppender(LoggerContext context,
                                                                  Path workspace,
                                                                  EngineLogBounds bounds) {
        Path logFile = engineLogFile(workspace);

        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            logger.warn("Cannot create the engine log directory '{}' ({}), engine logs are not written",
                    logFile.getParent(), e.getMessage());
            return null;
        }

        deleteEngineLogFiles(logFile);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(LOG_PATTERN);
        encoder.start();

        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setName(LOG_FILE_APPENDER_NAME);
        fileAppender.setContext(context);
        fileAppender.setFile(logFile.toString());
        fileAppender.setAppend(false);
        fileAppender.setEncoder(encoder);

        FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logFile + ".%i");
        rollingPolicy.setMinIndex(1);
        rollingPolicy.setMaxIndex(bounds.maxRolledFiles());
        rollingPolicy.start();

        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
        triggeringPolicy.setContext(context);
        triggeringPolicy.setMaxFileSize(bounds.maxFileSize());
        triggeringPolicy.setCheckIncrement(LOG_SIZE_CHECK_INTERVAL);
        triggeringPolicy.start();

        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setTriggeringPolicy(triggeringPolicy);
        fileAppender.start();

        if (!fileAppender.isStarted()) {
            logger.warn("Cannot open the engine log file '{}', engine logs are not written", logFile);
            return null;
        }

        return fileAppender;
    }

    /**
     * Removes the engine log files of an earlier run, so that this run writes its own engine log: the
     * current file is recreated and the rolled files of an earlier run cannot make this run's bound
     * report something the run did not write.
     */
    private static void deleteEngineLogFiles(Path logFile) {
        Path directory = logFile.getParent();
        String prefix = logFile.getFileName() + ".";

        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(logFile.getFileName().toString())
                            || path.getFileName().toString().startsWith(prefix))
                    .toList()) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    logger.warn("Cannot remove the engine log file '{}' of an earlier run ({})",
                            file, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warn("Cannot list the engine log directory '{}' ({})", directory, e.getMessage());
        }
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
