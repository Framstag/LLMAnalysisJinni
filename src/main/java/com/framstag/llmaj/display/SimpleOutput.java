package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
import dev.langchain4j.model.output.TokenUsage;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple sequential output for non-TTY environments (piped output, CI).
 * <p>
 * Prints one status line per event. No cursor manipulation, no colours.
 */
public class SimpleOutput implements ProgressCallback, AutoCloseable {
    private final PrintWriter writer;
    private final Config config;
    private final Instant startTime = Instant.now();
    private final AtomicInteger aggregateInputTokens = new AtomicInteger(0);
    private final AtomicInteger aggregateOutputTokens = new AtomicInteger(0);
    private final AtomicInteger aggregateTotalTokens = new AtomicInteger(0);
    private final Map<String, Long> taskTimes = new LinkedHashMap<>();
    private final Map<String, String> taskStatus = new LinkedHashMap<>();

    public SimpleOutput(Config config) {
        this.writer = new PrintWriter(System.out, true);
        this.config = config;
    }

    @Override
    public void onTaskStart(String taskId, String taskName) {
        startTask(taskId, taskName);
    }

    @Override
    public void onTaskComplete(String taskId, String taskName) {
        completeTask(taskId, taskName);
    }

    @Override
    public void onTaskError(String taskId, String taskName, String error) {
        failTask(taskId, taskName, error);
    }

    @Override
    public void onWorkerStart(String taskId, int index, String label) {
        startLoopWorker(taskId, index, label);
    }

    @Override
    public void onWorkerComplete(String taskId, int index, String label) {
        completeLoopWorker(taskId, index, label);
    }

    @Override
    public void onWorkerError(String taskId, int index, String label, String error) {
        failLoopWorker(taskId, index, label, error);
    }

    @Override
    public void onRequestSent(String taskId, Integer loopIndex) {
        // Not shown in simple mode
    }

    @Override
    public void onResponseReceived(String taskId, Integer loopIndex) {
        // Not shown in simple mode
    }

    @Override
    public void onToolCall(String taskId, Integer loopIndex, String toolName) {
        // Not shown in simple mode
    }

    @Override
    public void onToolResult(String taskId, Integer loopIndex, String toolName) {
        // Not shown in simple mode
    }

    @Override
    public void onTokenUsage(String taskId, Integer loopIndex, TokenUsage tokenUsage) {
        if (tokenUsage != null) {
            if (tokenUsage.inputTokenCount() != null) {
                aggregateInputTokens.addAndGet(tokenUsage.inputTokenCount());
            }
            if (tokenUsage.outputTokenCount() != null) {
                aggregateOutputTokens.addAndGet(tokenUsage.outputTokenCount());
            }
            if (tokenUsage.totalTokenCount() != null) {
                aggregateTotalTokens.addAndGet(tokenUsage.totalTokenCount());
            }
        }
    }

    @Override
    public void onComplete(String taskId, Integer loopIndex) {
        // Handled by completeTask/completeLoopWorker
    }

    @Override
    public void onError(String taskId, Integer loopIndex, String errorMessage) {
        if (loopIndex != null) {
            writer.println("  " + "\u2717" + " " + taskId + "[" + loopIndex + "]: " + errorMessage);
        } else {
            writer.println("  " + "\u2717" + " " + taskId + ": " + errorMessage);
        }
    }

    public void startTask(String taskId, String taskName) {
        taskStatus.put(taskId, "RUNNING");
        writer.println("\u25b6" + " " + taskName);
    }

    public void completeTask(String taskId, String taskName) {
        long elapsed = Duration.between(startTime, Instant.now()).toMillis();
        taskTimes.put(taskId, elapsed);
        taskStatus.put(taskId, "SUCCESSFUL");
        writer.println("\u2713" + " " + taskName + " (" + formatElapsed(elapsed) + ")");
    }

    public void failTask(String taskId, String taskName, String errorMessage) {
        long elapsed = Duration.between(startTime, Instant.now()).toMillis();
        taskTimes.put(taskId, elapsed);
        taskStatus.put(taskId, "FAILED");
        writer.println("\u2717" + " " + taskName + " (" + formatElapsed(elapsed) + ")" + (errorMessage != null ? ": " + errorMessage : ""));
    }

    public void startLoopWorker(String taskId, int index, String label) {
        writer.println("  " + "\u25b6" + " " + label);
    }

    public void completeLoopWorker(String taskId, int index, String label) {
        writer.println("  " + "\u2713" + " " + label);
    }

    public void failLoopWorker(String taskId, int index, String label, String errorMessage) {
        writer.println("  " + "\u2717" + " " + label + (errorMessage != null ? ": " + errorMessage : ""));
    }

    @Override
    public void close() {
        writer.println();
        writer.println("=== Analysis Complete ===");
        for (var entry : taskStatus.entrySet()) {
            String icon = switch (entry.getValue()) {
                case "SUCCESSFUL" -> "\u2713";
                case "FAILED" -> "\u2717";
                default -> "\u25b6";
            };
            Long time = taskTimes.get(entry.getKey());
            String timeStr = time != null ? " (" + formatElapsed(time) + ")" : "";
            writer.println("  " + icon + " " + entry.getKey() + timeStr);
        }
        writer.println();
        writer.println("Token: IN " + formatTokenCount(aggregateInputTokens.get())
                + "  OUT " + formatTokenCount(aggregateOutputTokens.get())
                + "  TOTAL " + formatTokenCount(aggregateTotalTokens.get()));
        writer.println("Total time: " + formatElapsed(Duration.between(startTime, Instant.now()).toMillis()));
        writer.flush();
    }

    private static String formatElapsed(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        double seconds = millis / 1000.0;
        if (seconds < 60) {
            return String.format("%.1fs", seconds);
        }
        long minutes = (long) seconds / 60;
        long secs = (long) seconds % 60;
        return String.format("%dm%02ds", minutes, secs);
    }

    private static String formatTokenCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        return String.format("%.1fK", count / 1000.0);
    }
}
