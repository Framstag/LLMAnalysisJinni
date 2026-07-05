package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
import dev.langchain4j.model.output.TokenUsage;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Live terminal UI showing task execution progress.
 * <p>
 * Implements ProgressCallback to receive real-time updates from ChatExecutor.
 * Renders a full-screen TUI with task list, per-worker interaction timelines,
 * loop progress, timing, and token usage.
 * <p>
 * Thread-safe: all public methods are synchronized. Render reads model under lock.
 */
public class ProgressDisplay implements ProgressCallback, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ProgressDisplay.class);

    private static final int RENDER_INTERVAL_MS = 500;

    private final Terminal terminal;
    private final PrintWriter writer;
    private final Config config;
    private final boolean ansiSupported;
    private final boolean unicodeSupported;

    // Display model
    private final Map<String, TaskRow> taskMap = new LinkedHashMap<>();
    private final List<TaskRow> taskOrder = new ArrayList<>();
    private final Map<String, List<LoopWorkerRow>> workerMap = new LinkedHashMap<>();
    private final AtomicInteger aggregateInputTokens = new AtomicInteger(0);
    private final AtomicInteger aggregateOutputTokens = new AtomicInteger(0);
    private final AtomicInteger aggregateTotalTokens = new AtomicInteger(0);
    private final Instant startTime = Instant.now();
    private final AtomicReference<String> currentTaskId = new AtomicReference<>(null);

    // Per-worker timing
    private final Map<String, Instant> workerStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> workerElapsed = new ConcurrentHashMap<>();

    // Per-task timing
    private final Map<String, Instant> taskStartTimes = new ConcurrentHashMap<>();

    // Render timer
    private final ScheduledExecutorService renderTimer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "progress-display-render");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean closed = false;

    public ProgressDisplay(Config config) throws IOException {
        this.config = config;
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        this.writer = terminal.writer();
        this.ansiSupported = System.console() != null
                && terminal.getType() != null
                && !terminal.getType().equalsIgnoreCase("dumb")
                && !terminal.getType().equalsIgnoreCase("unknown");
        this.unicodeSupported = ansiSupported && terminal.encoding() != null
                && terminal.encoding().name().toUpperCase().contains("UTF");

        // Hide cursor
        if (ansiSupported) {
            writer.print(Ansi.HIDE_CURSOR);
            writer.flush();
        }

        // Start render timer
        renderTimer.scheduleAtFixedRate(this::render, RENDER_INTERVAL_MS, RENDER_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ========== ProgressCallback implementation ==========

    @Override
    public synchronized void onTaskStart(String taskId, String taskName) {
        startTask(taskId);
    }

    @Override
    public synchronized void onTaskComplete(String taskId, String taskName) {
        completeTask(taskId);
    }

    @Override
    public synchronized void onTaskError(String taskId, String taskName, String error) {
        failTask(taskId, error);
    }

    @Override
    public synchronized void onWorkerStart(String taskId, int index, String label) {
        addLoopWorker(taskId, index, label);
    }

    @Override
    public synchronized void onWorkerComplete(String taskId, int index, String label) {
        completeLoopWorker(taskId, index);
    }

    @Override
    public synchronized void onWorkerError(String taskId, int index, String label, String error) {
        failLoopWorker(taskId, index, error);
    }

    @Override
    public synchronized void onRequestSent(String taskId, Integer loopIndex) {
        if (loopIndex != null) {
            var worker = findWorker(taskId, loopIndex);
            if (worker != null) {
                worker.addStep(LoopWorkerRow.InteractionStep.REQUEST_SENT);
                worker.setStatus(LoopWorkerRow.Status.RUNNING);
            }
        } else {
            var task = taskMap.get(taskId);
            if (task != null) {
                task.addStep(LoopWorkerRow.InteractionStep.REQUEST_SENT);
            }
        }
    }

    @Override
    public synchronized void onResponseReceived(String taskId, Integer loopIndex) {
        if (loopIndex != null) {
            var worker = findWorker(taskId, loopIndex);
            if (worker != null) {
                worker.addStep(LoopWorkerRow.InteractionStep.RESPONSE_RECEIVED);
                worker.incrementRoundCount();
            }
        } else {
            var task = taskMap.get(taskId);
            if (task != null) {
                task.addStep(LoopWorkerRow.InteractionStep.RESPONSE_RECEIVED);
            }
        }
    }

    @Override
    public synchronized void onToolCall(String taskId, Integer loopIndex, String toolName) {
        if (loopIndex != null) {
            var worker = findWorker(taskId, loopIndex);
            if (worker != null) {
                worker.addStep(LoopWorkerRow.InteractionStep.TOOL_CALL);
            }
        } else {
            var task = taskMap.get(taskId);
            if (task != null) {
                task.addStep(LoopWorkerRow.InteractionStep.TOOL_CALL);
            }
        }
    }

    @Override
    public synchronized void onToolResult(String taskId, Integer loopIndex, String toolName) {
        if (loopIndex != null) {
            var worker = findWorker(taskId, loopIndex);
            if (worker != null) {
                worker.addStep(LoopWorkerRow.InteractionStep.TOOL_RESULT);
            }
        } else {
            var task = taskMap.get(taskId);
            if (task != null) {
                task.addStep(LoopWorkerRow.InteractionStep.TOOL_RESULT);
            }
        }
    }

    @Override
    public synchronized void onTokenUsage(String taskId, Integer loopIndex, TokenUsage tokenUsage) {
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
    public synchronized void onComplete(String taskId, Integer loopIndex) {
        if (loopIndex != null) {
            var worker = findWorker(taskId, loopIndex);
            if (worker != null) {
                worker.setStatus(LoopWorkerRow.Status.SUCCESSFUL);
                worker.setElapsedMillis(System.currentTimeMillis() -
                        workerStartTimes.getOrDefault(key(taskId, loopIndex), Instant.now()).toEpochMilli());
            }
        }
    }

    @Override
    public synchronized void onError(String taskId, Integer loopIndex, String errorMessage) {
        if (loopIndex != null) {
            var worker = findWorker(taskId, loopIndex);
            if (worker != null) {
                worker.setStatus(LoopWorkerRow.Status.FAILED);
                worker.setErrorMessage(errorMessage);
            }
        } else {
            var task = taskMap.get(taskId);
            if (task != null) {
                task.setStatus(TaskRow.Status.FAILED);
                task.setErrorMessage(errorMessage);
            }
        }
    }

    // ========== Display model methods ==========

    public synchronized void addTasks(List<com.framstag.llmaj.tasks.TaskDefinition> tasks) {
        for (var task : tasks) {
            var row = new TaskRow(task.getId(), task.getName());
            row.setHasLoop(task.hasLoopOn());
            taskMap.put(task.getId(), row);
            taskOrder.add(row);
        }
        render();
    }

    public synchronized void startTask(String taskId) {
        var task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus(TaskRow.Status.RUNNING);
            taskStartTimes.put(taskId, Instant.now());
            currentTaskId.set(taskId);
        }
    }

    public synchronized void setLoopTotal(String taskId, int total) {
        var task = taskMap.get(taskId);
        if (task != null) {
            task.setLoopTotal(total);
        }
    }

    public synchronized void completeTask(String taskId) {
        var task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus(TaskRow.Status.SUCCESSFUL);
            Instant taskStart = taskStartTimes.get(taskId);
            if (taskStart != null) {
                task.setElapsedMillis(Duration.between(taskStart, Instant.now()).toMillis());
            }
        }
    }

    /**
     * Mark a task as successful without setting elapsed time.
     * Used for tasks already completed in a previous run.
     */
    public synchronized void markTaskPreCompleted(String taskId) {
        var task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus(TaskRow.Status.SUCCESSFUL);
            // No elapsed time — task was done before this run
        }
    }

    public synchronized void failTask(String taskId, String errorMessage) {
        var task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus(TaskRow.Status.FAILED);
            task.setErrorMessage(errorMessage);
        }
    }

    public synchronized void addLoopWorker(String taskId, int index, String label) {
        var worker = new LoopWorkerRow(taskId, index, label);
        worker.setStatus(LoopWorkerRow.Status.RUNNING);
        workerMap.computeIfAbsent(taskId, k -> new ArrayList<>()).add(worker);
        workerStartTimes.put(key(taskId, index), Instant.now());
    }

    public synchronized void completeLoopWorker(String taskId, int index) {
        var worker = findWorker(taskId, index);
        if (worker != null) {
            worker.setStatus(LoopWorkerRow.Status.SUCCESSFUL);
            worker.setElapsedMillis(System.currentTimeMillis() -
                    workerStartTimes.getOrDefault(key(taskId, index), Instant.now()).toEpochMilli());
        }
        var task = taskMap.get(taskId);
        if (task != null) {
            task.incrementLoopCompleted();
        }
    }

    public synchronized void failLoopWorker(String taskId, int index, String errorMessage) {
        var worker = findWorker(taskId, index);
        if (worker != null) {
            worker.setStatus(LoopWorkerRow.Status.FAILED);
            worker.setErrorMessage(errorMessage);
        }
    }

    public synchronized void updateTokenUsage(String taskId, TokenUsage tokenUsage) {
        var task = taskMap.get(taskId);
        if (task != null) {
            task.setTokenUsage(tokenUsage);
        }
    }

    // ========== Rendering ==========

    // ── Interaction step helpers ──────────────────────────────────────

    private static String stepSymbol(LoopWorkerRow.InteractionStep step, boolean unicode) {
        if (unicode) {
            return switch (step) {
                case REQUEST_SENT -> "\u2192";      // →
                case RESPONSE_RECEIVED -> "\u2190";  // ←
                case TOOL_CALL -> "\u25c6";          // ◆
                case TOOL_RESULT -> "\u2713";         // ✓
            };
        } else {
            return switch (step) {
                case REQUEST_SENT -> "->";
                case RESPONSE_RECEIVED -> "<-";
                case TOOL_CALL -> "!";
                case TOOL_RESULT -> "ok";
            };
        }
    }

    private static String stepColour(LoopWorkerRow.InteractionStep step) {
        return switch (step) {
            case REQUEST_SENT -> Ansi.YELLOW;
            case RESPONSE_RECEIVED -> Ansi.CYAN;
            case TOOL_CALL -> Ansi.MAGENTA;
            case TOOL_RESULT -> Ansi.GREEN;
        };
    }

    private static String stepColourBright(LoopWorkerRow.InteractionStep step) {
        return switch (step) {
            case REQUEST_SENT -> Ansi.BRIGHT_YELLOW;
            case RESPONSE_RECEIVED -> Ansi.BRIGHT_CYAN;
            case TOOL_CALL -> Ansi.BRIGHT_MAGENTA;
            case TOOL_RESULT -> Ansi.BRIGHT_GREEN;
        };
    }

    private synchronized void render() {
        if (closed) return;

        int height = terminal.getHeight();
        int width = terminal.getWidth();

        // Calculate how many lines we need
        int totalLines = countLines(width);

        // If terminal too small, show minimal output
        if (height < 5 || width < 40) {
            renderMinimal(width);
            return;
        }

        // Move cursor to top of display area
        writer.print(Ansi.cursorUp(totalLines));
        writer.print(Ansi.ERASE_DISPLAY);
        writer.print(Ansi.CURSOR_HOME);

        // Render header
        renderHeader(width);

        // Render task list
        renderTasks(width, height);

        // Render footer
        renderFooter(width);

        writer.flush();
    }

    private int countLines(int width) {
        int lines = 2; // header + blank
        for (var task : taskOrder) {
            lines++; // task row
            var workers = workerMap.get(task.getId());
            if (workers != null && !workers.isEmpty()) {
                lines += workers.size(); // worker sub-rows
            }
        }
        lines += 2; // blank + footer
        return lines;
    }

    private void renderHeader(int width) {
        String projectInfo = "LLMAnalysisJinni \u2014 " + config.getAnalysisDirectoryAsString();
        String modelInfo = config.getModelName() + " (" + config.getModelProvider() + ")";
        String elapsed = "Elapsed: " + Ansi.formatElapsed(Duration.between(startTime, Instant.now()).toMillis());

        if (ansiSupported) {
            writer.println(Ansi.bold(projectInfo));
            writer.println(modelInfo + "  │  " + elapsed);
        } else {
            writer.println(projectInfo);
            writer.println(modelInfo + "  |  " + elapsed);
        }
        writer.println();
    }

    private void renderTasks(int width, int height) {
        int maxRows = height - 5; // header(2) + blank(1) + footer(2)
        int shown = 0;
        boolean overflow = false;

        for (var task : taskOrder) {
            if (shown >= maxRows) {
                overflow = true;
                break;
            }

            renderTaskRow(task, width);
            shown++;

            // Only show worker sub-rows for tasks still in progress
            var workers = workerMap.get(task.getId());
            if (workers != null && !workers.isEmpty()
                    && task.getStatus() != TaskRow.Status.SUCCESSFUL
                    && task.getStatus() != TaskRow.Status.FAILED) {
                for (var worker : workers) {
                    if (shown >= maxRows) {
                        overflow = true;
                        break;
                    }
                    renderWorkerRow(worker, width);
                    shown++;
                }
            }
        }

        if (overflow) {
            writer.println(Ansi.colour("  \u2026 (more rows not shown)", Ansi.DIM));
        }
    }

    private void renderTaskRow(TaskRow task, int width) {
        StringBuilder sb = new StringBuilder();

        // Status icon
        String icon = switch (task.getStatus()) {
            case PENDING -> "\u2026";
            case RUNNING -> "\u25b6";
            case SUCCESSFUL -> "\u2713";
            case FAILED -> "\u2717";
        };

        // Colour the icon
        String iconColour = switch (task.getStatus()) {
            case PENDING -> Ansi.DIM;
            case RUNNING -> Ansi.BRIGHT_CYAN;
            case SUCCESSFUL -> Ansi.GREEN;
            case FAILED -> Ansi.RED;
        };

        if (ansiSupported) {
            sb.append(Ansi.colour(icon, iconColour)).append(" ");
        } else {
            sb.append(icon).append(" ");
        }

        // Task name
        String name = task.getName();
        if (ansiSupported && task.getStatus() == TaskRow.Status.RUNNING) {
            name = Ansi.colour(name, Ansi.BRIGHT_CYAN);
        }
        sb.append(name);

        // Elapsed time
        long elapsed = task.getElapsedMillis();
        if (elapsed > 0 || task.getStatus() == TaskRow.Status.RUNNING) {
            if (task.getStatus() == TaskRow.Status.RUNNING) {
                Instant taskStart = taskStartTimes.get(task.getId());
                if (taskStart != null) {
                    elapsed = Duration.between(taskStart, Instant.now()).toMillis();
                }
            }
            String timeStr = Ansi.formatElapsed(elapsed);
            if (ansiSupported) {
                timeStr = Ansi.dim(timeStr);
            }
            sb.append("  ").append(timeStr);
        }

        // Interaction timeline (non-loop tasks)
        if (!task.hasLoop() && !task.getSteps().isEmpty()) {
            sb.append("  [");
            for (int i = 0; i < task.getSteps().size(); i++) {
                var step = task.getSteps().get(i);
                String sym = stepSymbol(step, unicodeSupported);
                boolean isLast = (i == task.getSteps().size() - 1);
                if (ansiSupported) {
                    String colour = isLast ? stepColourBright(step) : Ansi.DIM;
                    sym = Ansi.colour(sym, colour);
                }
                sb.append(sym);
            }
            sb.append("]");
        }

        // Token usage
        if (task.getTokenUsage() != null) {
            var tu = task.getTokenUsage();
            String tokenStr = "IN:" + Ansi.formatTokenCount(tu.inputTokenCount())
                    + " OUT:" + Ansi.formatTokenCount(tu.outputTokenCount());
            if (ansiSupported) {
                tokenStr = Ansi.dim(tokenStr);
            }
            sb.append("  ").append(tokenStr);
        }

        // Loop progress
        if (task.hasLoop() && task.getLoopTotal() > 0) {
            String loopStr = task.getLoopCompleted() + "/" + task.getLoopTotal();
            if (ansiSupported) {
                loopStr = Ansi.colour(loopStr, Ansi.YELLOW);
            }
            sb.append("  ").append(loopStr);

            // Progress bar
            if (width > 60) {
                int barWidth = Math.min(20, width - sb.length() - 2);
                if (barWidth > 5) {
                    String bar = Ansi.progressBar(task.getLoopCompleted(), task.getLoopTotal(), barWidth);
                    if (ansiSupported) {
                        bar = Ansi.colour(bar, Ansi.GREEN);
                    }
                    sb.append("  ").append(bar);
                }
            }
        }

        // Error message
        if (task.getErrorMessage() != null) {
            String err = task.getErrorMessage();
            int maxErrLen = width - sb.length() - 3;
            if (maxErrLen > 10) {
                if (err.length() > maxErrLen) {
                    err = err.substring(0, maxErrLen - 3) + "...";
                }
                if (ansiSupported) {
                    err = Ansi.colour(err, Ansi.RED);
                }
                sb.append("  ").append(err);
            }
        }

        writer.println(sb.toString());
    }

    private void renderWorkerRow(LoopWorkerRow worker, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append("  \u2514");

        // Status icon
        String icon = switch (worker.getStatus()) {
            case PENDING -> "\u2026";
            case RUNNING -> "\u25b6";
            case SUCCESSFUL -> "\u2713";
            case FAILED -> "\u2717";
        };

        String iconColour = switch (worker.getStatus()) {
            case PENDING -> Ansi.DIM;
            case RUNNING -> Ansi.BRIGHT_CYAN;
            case SUCCESSFUL -> Ansi.GREEN;
            case FAILED -> Ansi.RED;
        };

        if (ansiSupported) {
            sb.append(Ansi.colour(icon, iconColour)).append(" ");
        } else {
            sb.append(icon).append(" ");
        }

        // Worker label
        String label = worker.getLabel();
        if (ansiSupported && worker.getStatus() == LoopWorkerRow.Status.RUNNING) {
            label = Ansi.colour(label, Ansi.BRIGHT_CYAN);
        }
        sb.append(label);

        // Elapsed time
        long elapsed = worker.getElapsedMillis();
        if (elapsed > 0 || worker.getStatus() == LoopWorkerRow.Status.RUNNING) {
            if (worker.getStatus() == LoopWorkerRow.Status.RUNNING && workerStartTimes.containsKey(key(worker.getTaskId(), worker.getIndex()))) {
                elapsed = System.currentTimeMillis() -
                        workerStartTimes.get(key(worker.getTaskId(), worker.getIndex())).toEpochMilli();
            }
            String timeStr = Ansi.formatElapsed(elapsed);
            if (ansiSupported) {
                timeStr = Ansi.dim(timeStr);
            }
            sb.append("  ").append(timeStr);
        }

        // Interaction timeline
        if (!worker.getSteps().isEmpty()) {
            sb.append("  [");
            for (int i = 0; i < worker.getSteps().size(); i++) {
                var step = worker.getSteps().get(i);
                String sym = stepSymbol(step, unicodeSupported);
                boolean isLast = (i == worker.getSteps().size() - 1);
                if (ansiSupported) {
                    String colour = isLast ? stepColourBright(step) : Ansi.DIM;
                    sym = Ansi.colour(sym, colour);
                }
                sb.append(sym);
            }
            sb.append("]");
        }

        // Error message
        if (worker.getErrorMessage() != null) {
            String err = worker.getErrorMessage();
            int maxErrLen = width - sb.length() - 3;
            if (maxErrLen > 10) {
                if (err.length() > maxErrLen) {
                    err = err.substring(0, maxErrLen - 3) + "...";
                }
                if (ansiSupported) {
                    err = Ansi.colour(err, Ansi.RED);
                }
                sb.append("  ").append(err);
            }
        }

        writer.println(sb.toString());
    }

    private void renderFooter(int width) {
        writer.println();
        StringBuilder sb = new StringBuilder();
        sb.append("Token: IN ").append(Ansi.formatTokenCount(aggregateInputTokens.get()))
                .append("  OUT ").append(Ansi.formatTokenCount(aggregateOutputTokens.get()))
                .append("  TOTAL ").append(Ansi.formatTokenCount(aggregateTotalTokens.get()));
        if (ansiSupported) {
            sb.insert(0, Ansi.DIM);
            sb.append(Ansi.RESET);
        }
        writer.println(sb.toString());
    }

    private void renderMinimal(int width) {
        // Just show current task name
        String taskId = currentTaskId.get();
        if (taskId != null) {
            var task = taskMap.get(taskId);
            if (task != null) {
                writer.print("\r" + " ".repeat(width - 1) + "\r");
                writer.print("\u25b6 " + task.getName() + "  "
                        + Ansi.formatElapsed(Duration.between(startTime, Instant.now()).toMillis()));
                writer.flush();
            }
        }
    }

    // ========== Close ==========

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;

        renderTimer.shutdown();
        try {
            renderTimer.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Restore cursor
        if (ansiSupported) {
            writer.print(Ansi.SHOW_CURSOR);
            writer.print(Ansi.RESET);
        }

        // Print final summary
        writer.println();
        writer.println(Ansi.bold("=== Analysis Complete ==="));
        for (var task : taskOrder) {
            String icon = switch (task.getStatus()) {
                case PENDING -> "\u2026";
                case RUNNING -> "\u25b6";
                case SUCCESSFUL -> "\u2713";
                case FAILED -> "\u2717";
            };
            String time = task.getElapsedMillis() > 0 ? " (" + Ansi.formatElapsed(task.getElapsedMillis()) + ")" : "";
            writer.println("  " + icon + " " + task.getName() + time);
        }
        writer.println();
        writer.println("Token: IN " + Ansi.formatTokenCount(aggregateInputTokens.get())
                + "  OUT " + Ansi.formatTokenCount(aggregateOutputTokens.get())
                + "  TOTAL " + Ansi.formatTokenCount(aggregateTotalTokens.get()));
        writer.println("Total time: " + Ansi.formatElapsed(Duration.between(startTime, Instant.now()).toMillis()));
        writer.flush();
    }

    // ========== Helpers ==========

    private LoopWorkerRow findWorker(String taskId, int index) {
        var workers = workerMap.get(taskId);
        if (workers == null) return null;
        return workers.stream()
                .filter(w -> w.getIndex() == index)
                .findFirst()
                .orElse(null);
    }

    private static String key(String taskId, int index) {
        return taskId + ":" + index;
    }
}
