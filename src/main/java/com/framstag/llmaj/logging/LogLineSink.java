package com.framstag.llmaj.logging;

/**
 * Receives log records that must not be written to the terminal, for example while the TUI owns it.
 * <p>
 * Implementations must be safe to call from any thread, because log records come from the task and
 * loop worker threads.
 */
public interface LogLineSink {

    /**
     * Called for a log record that is meant to be shown inside the display.
     */
    void onLogLine(LogLine line);

    /**
     * Sink that drops every record, for runs in which the console keeps the log output.
     */
    static LogLineSink noOp() {
        return line -> {};
    }

    /**
     * Sink whose target can be set once the display exists. The log routing is installed before the
     * display is created, so the records that arrive in between need a sink that already exists.
     */
    static ForwardingLogLineSink forwarding() {
        return new ForwardingLogLineSink();
    }
}
