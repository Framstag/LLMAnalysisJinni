package com.framstag.llmaj.display;

import com.framstag.llmaj.config.Config;
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

    /**
     * Create display manager.
     *
     * @param config               analysis config
     * @param useTui               true to use TUI ProgressDisplay
     * @param executionTrace       true for execution-trace mode (no display)
     * @param allTasks             all task definitions (for TUI pre-population)
     * @param preCompletedTaskIds  set of task IDs already completed from previous run
     * @throws java.io.IOException if TUI terminal creation fails
     */
    public DisplayManager(Config config,
                          boolean useTui,
                          boolean executionTrace,
                          List<TaskDefinition> allTasks,
                          Set<String> preCompletedTaskIds) throws java.io.IOException {
        if (useTui) {
            var d = new ProgressDisplay(config);
            d.addTasks(allTasks);
            for (var task : allTasks) {
                if (preCompletedTaskIds.contains(task.getId())) {
                    d.markTaskPreCompleted(task.getId());
                }
            }
            this.display = d;
            this.callback = d;
            this.simple = null;
        } else if (!executionTrace) {
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
            display.completeTask(taskId);
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
        if (display != null) {
            display.close();
        }
        if (simple != null) {
            simple.close();
        }
    }
}
