package com.framstag.llmaj.display;

import dev.langchain4j.model.output.TokenUsage;

/**
 * Callback interface for reporting LLM interaction progress from ChatExecutor
 * to the display layer.
 * <p>
 * Each method is called from the worker thread that owns the ChatExecutor.
 * Implementations must be thread-safe.
 */
public interface ProgressCallback {

    /**
     * Called when a request is sent to the LLM.
     */
    void onRequestSent(String taskId, Integer loopIndex);

    /**
     * Called when a response is received from the LLM.
     */
    void onResponseReceived(String taskId, Integer loopIndex);

    /**
     * Called when a tool execution request is sent.
     */
    void onToolCall(String taskId, Integer loopIndex, String toolName);

    /**
     * Called when a tool execution result is received.
     */
    void onToolResult(String taskId, Integer loopIndex, String toolName);

    /**
     * Called when token usage is available after a response.
     */
    void onTokenUsage(String taskId, Integer loopIndex, TokenUsage tokenUsage);

    /**
     * Called when all interactions for a task/worker complete successfully.
     */
    void onComplete(String taskId, Integer loopIndex);

    /**
     * Called when a task/worker encounters an error.
     */
    void onError(String taskId, Integer loopIndex, String errorMessage);

    /**
     * Called when a task starts execution.
     */
    default void onTaskStart(String taskId, String taskName) {}

    /**
     * Called when a task completes successfully.
     */
    default void onTaskComplete(String taskId, String taskName) {}

    /**
     * Called when a task fails.
     */
    default void onTaskError(String taskId, String taskName, String error) {}

    /**
     * Called when a loop worker starts.
     */
    default void onWorkerStart(String taskId, int index, String label) {}

    /**
     * Called when a loop worker completes successfully.
     */
    default void onWorkerComplete(String taskId, int index, String label) {}

    /**
     * Called when a loop worker fails.
     */
    default void onWorkerError(String taskId, int index, String label, String error) {}

    /**
     * No-op implementation for use when no display is active.
     */
    static ProgressCallback noOp() {
        return new ProgressCallback() {
            @Override
            public void onRequestSent(String taskId, Integer loopIndex) {}

            @Override
            public void onResponseReceived(String taskId, Integer loopIndex) {}

            @Override
            public void onToolCall(String taskId, Integer loopIndex, String toolName) {}

            @Override
            public void onToolResult(String taskId, Integer loopIndex, String toolName) {}

            @Override
            public void onTokenUsage(String taskId, Integer loopIndex, TokenUsage tokenUsage) {}

            @Override
            public void onComplete(String taskId, Integer loopIndex) {}

            @Override
            public void onError(String taskId, Integer loopIndex, String errorMessage) {}
        };
    }
}
