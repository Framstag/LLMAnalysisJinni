package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.logging.ForwardingLogLineSink;
import com.framstag.llmaj.tasks.TaskDefinition;

import java.util.List;
import java.util.Set;

/**
 * Unified display manager that routes to ProgressDisplay (TUI), SimpleOutput (piped/CI),
 * or no-op (execution-trace mode).
 * <p>
 * Replaces inline if/else branching in AnalyseCmd.
 */
public class DisplayManager implements AutoCloseable {
    private final ProgressDisplay display;
    private final SimpleOutput simple;
    private final ProgressCallback callback;
    private final ForwardingLogLineSink logLineSink;

    /**
     * Create display manager.
     *
     * @param config               analysis config
     * @param decision             which display the run uses, and why
     * @param terminalSupport      terminal and its capabilities, used when the TUI is chosen
     * @param allTasks             all task definitions (for TUI pre-population)
     * @param preCompletedTaskIds  set of task IDs already completed from previous run
     * @param logLineSink          sink for the log records the engine log routing diverts; the TUI
     *                             registers itself here, every other display leaves it without a
     *                             target
     */
    public DisplayManager(Config config,
                          DisplayDecision decision,
                          TerminalSupport terminalSupport,
                          List<TaskDefinition> allTasks,
                          Set<String> preCompletedTaskIds,
                          ForwardingLogLineSink logLineSink) {
        this.logLineSink = logLineSink;

        if (decision.useTui()) {
            var d = new ProgressDisplay(config,
                    terminalSupport.terminal(),
                    terminalSupport.ansiSupported(),
                    terminalSupport.unicodeSupported());
            d.addTasks(allTasks, preCompletedTaskIds);
            this.display = d;
            this.callback = d;
            this.simple = null;
            this.logLineSink.setTarget(d);
        } else if (decision.mode() == DisplayDecision.Mode.SIMPLE) {
            var s = new SimpleOutput(config);
            this.display = null;
            this.callback = s;
            this.simple = s;
        } else {
            this.display = null;
            this.callback = ProgressCallback.noOp();
            this.simple = null;
        }
    }

    /**
     * Return the ProgressCallback for ChatExecutor to report fine-grained events.
     */
    public ProgressCallback getCallback() {
        return callback;
    }

    /**
     * Notify display that a task has started.
     */
    public void onTaskStart(String taskId, String taskName) {
        if (display != null) {
            display.startTask(taskId);
        } else if (simple != null) {
            simple.startTask(taskId, taskName);
        }
    }

    /**
     * Notify display that a task has completed successfully.
     */
    public void onTaskComplete(String taskId, String taskName) {
        if (display != null) {
            display.completeTask(taskId);
        } else if (simple != null) {
            simple.completeTask(taskId, taskName);
        }
    }

    /**
     * Notify display that a task has failed.
     */
    public void onTaskError(String taskId, String taskName, String error) {
        if (display != null) {
            display.failTask(taskId, error);
        } else if (simple != null) {
            simple.failTask(taskId, taskName, error);
        }
    }

    /**
     * Set total loop iterations for a loop task (TUI only).
     */
    public void setLoopTotal(String taskId, int total) {
        if (display != null) {
            display.setLoopTotal(taskId, total);
        }
    }

    @Override
    public void close() {
        // The display is going away, so records of a finished run must not be collected any more.
        logLineSink.clearTarget();

        if (display != null) {
            display.close();
        }
        if (simple != null) {
            simple.close();
        }
    }
}
