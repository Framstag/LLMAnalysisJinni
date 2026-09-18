package com.framstag.llmaj.logging;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Sink that forwards to a target which is only known later. Until a target is set, records are
 * dropped.
 */
public final class ForwardingLogLineSink implements LogLineSink {

    private final AtomicReference<LogLineSink> target = new AtomicReference<>(LogLineSink.noOp());

    /**
     * Sets the display that receives the records from now on.
     */
    public void setTarget(LogLineSink target) {
        this.target.set(target == null ? LogLineSink.noOp() : target);
    }

    /**
     * Drops the target again, so records of a closed display are not collected.
     */
    public void clearTarget() {
        this.target.set(LogLineSink.noOp());
    }

    @Override
    public void onLogLine(LogLine line) {
        target.get().onLogLine(line);
    }
}
