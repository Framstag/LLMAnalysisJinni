package com.framstag.llmaj.display;

import java.util.ArrayList;
import java.util.List;

/**
 * Display model for one loop worker sub-row in the progress TUI.
 */
public class LoopWorkerRow {
    public enum Status {
        PENDING,
        RUNNING,
        SUCCESSFUL,
        FAILED
    }

    public enum InteractionStep {
        REQUEST_SENT,      // →  request sent to LLM
        RESPONSE_RECEIVED, // ←  response received from LLM
        TOOL_CALL,         // ⚡  tool call executed
        TOOL_RESULT        // ✓  tool result received
    }

    private final String taskId;
    private final int index;
    private final String label;
    private Status status;
    private long elapsedMillis;
    private final List<InteractionStep> steps;
    private int roundCount;
    private String errorMessage;

    public LoopWorkerRow(String taskId, int index, String label) {
        this.taskId = taskId;
        this.index = index;
        this.label = label;
        this.status = Status.PENDING;
        this.elapsedMillis = 0;
        this.steps = new ArrayList<>();
        this.roundCount = 0;
        this.errorMessage = null;
    }

    public String getTaskId() { return taskId; }
    public int getIndex() { return index; }
    public String getLabel() { return label; }
    public Status getStatus() { return status; }
    public long getElapsedMillis() { return elapsedMillis; }
    public List<InteractionStep> getSteps() { return steps; }
    public int getRoundCount() { return roundCount; }
    public String getErrorMessage() { return errorMessage; }

    public void setStatus(Status status) { this.status = status; }
    public void setElapsedMillis(long elapsedMillis) { this.elapsedMillis = elapsedMillis; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void addStep(InteractionStep step) {
        steps.add(step);
    }

    public void incrementRoundCount() {
        roundCount++;
    }
}
