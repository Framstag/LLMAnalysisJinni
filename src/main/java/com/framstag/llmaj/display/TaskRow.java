package com.framstag.llmaj.display;

import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;

/**
 * Display model for one task row in the progress TUI.
 */
public class TaskRow {
    public enum Status {
        PENDING,
        RUNNING,
        SUCCESSFUL,
        FAILED
    }

    private final String id;
    private final String name;
    private Status status;
    private long elapsedMillis;
    private TokenUsage tokenUsage;
    private String errorMessage;
    private int loopCompleted;
    private int loopTotal;
    private boolean hasLoop;
    private final List<LoopWorkerRow.InteractionStep> steps;

    public TaskRow(String id, String name) {
        this.id = id;
        this.name = name;
        this.status = Status.PENDING;
        this.elapsedMillis = 0;
        this.tokenUsage = null;
        this.errorMessage = null;
        this.loopCompleted = 0;
        this.loopTotal = 0;
        this.hasLoop = false;
        this.steps = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Status getStatus() { return status; }
    public long getElapsedMillis() { return elapsedMillis; }
    public TokenUsage getTokenUsage() { return tokenUsage; }
    public String getErrorMessage() { return errorMessage; }
    public int getLoopCompleted() { return loopCompleted; }
    public int getLoopTotal() { return loopTotal; }
    public boolean hasLoop() { return hasLoop; }
    public List<LoopWorkerRow.InteractionStep> getSteps() { return steps; }

    public void setStatus(Status status) { this.status = status; }
    public void setElapsedMillis(long elapsedMillis) { this.elapsedMillis = elapsedMillis; }
    public void setTokenUsage(TokenUsage tokenUsage) { this.tokenUsage = tokenUsage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setLoopCompleted(int loopCompleted) { this.loopCompleted = loopCompleted; }
    public void setLoopTotal(int loopTotal) { this.loopTotal = loopTotal; }
    public void setHasLoop(boolean hasLoop) { this.hasLoop = hasLoop; }

    public void incrementLoopCompleted() {
        this.loopCompleted++;
    }

    public void addStep(LoopWorkerRow.InteractionStep step) {
        steps.add(step);
    }
}
