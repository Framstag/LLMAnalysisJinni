package com.framstag.llmaj.logging;

/**
 * One log record as the display layer needs to see it: the level, the logger that emitted it, the
 * task it belongs to when the execution set one, and the already formatted message.
 */
public record LogLine(String level, String loggerName, String taskId, String message) {
}
