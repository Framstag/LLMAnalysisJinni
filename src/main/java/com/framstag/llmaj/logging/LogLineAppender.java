package com.framstag.llmaj.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Forwards warning and error records to a display that owns the terminal, so a condition without a
 * task row of its own is visible during the run instead of being written into the frame.
 * <p>
 * Records below {@link Level#WARN} are dropped: everything else is in the log file.
 */
public class LogLineAppender extends AppenderBase<ILoggingEvent> {

    private final LogLineSink sink;

    public LogLineAppender(LogLineSink sink) {
        this.sink = sink;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.WARN)) {
            return;
        }

        sink.onLogLine(new LogLine(
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getMDCPropertyMap().get(EngineLogRouting.TASK_ID_MDC_KEY),
                event.getFormattedMessage()));
    }
}
