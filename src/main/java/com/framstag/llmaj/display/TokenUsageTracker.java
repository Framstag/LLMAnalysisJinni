package com.framstag.llmaj.display;

import dev.langchain4j.model.output.TokenUsage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds up the token counts of a run, per step and over all steps.
 * <p>
 * The engine reports the running aggregate of a step every time a model answered, so the same
 * counts arrive more than once. Adding every value that arrives would count the first response of
 * a step once, the second response once, and then the two of them again as part of the running
 * aggregate. The tracker therefore remembers the last value it saw for a step and adds only what
 * the step has spent since then.
 * <p>
 * A step that is attempted again starts a new aggregate, so the tracker forgets the last value of
 * that step when the attempt is reported as failed and a further one is started. The counts of the
 * attempt that failed stay part of the total, because they were spent.
 */
final class TokenUsageTracker {

    /**
     * Identifies one step: a non-loop task, or one loop index of a loop task.
     */
    record StepKey(String taskId, Integer loopIndex) {
    }

    /**
     * The last running aggregate that was reported for a step.
     */
    private final Map<StepKey, TokenUsage> lastReported = new LinkedHashMap<>();

    /**
     * What a step has spent since the run began.
     */
    private final Map<StepKey, TokenUsage> accumulated = new LinkedHashMap<>();

    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;
    private int totalTokenCount = 0;

    /**
     * Records what a step has reported and adds what it has spent since its last report.
     *
     * @param taskId    the task the step belongs to
     * @param loopIndex the loop index of the step, or null for a non-loop task
     * @param usage     the running aggregate of the step, or null when nothing is known
     */
    void report(String taskId, Integer loopIndex, TokenUsage usage) {
        if (usage == null) {
            return;
        }

        StepKey key = new StepKey(taskId, loopIndex);
        TokenUsage previous = lastReported.get(key);
        TokenUsage delta = delta(previous, usage);

        totalInputTokens += orZero(delta.inputTokenCount());
        totalOutputTokens += orZero(delta.outputTokenCount());
        totalTokenCount += orZero(delta.totalTokenCount());

        accumulated.put(key, add(accumulated.get(key), delta));
        lastReported.put(key, usage);
    }

    /**
     * Forgets the last value reported for a step, because a further attempt of that step starts a
     * new aggregate. The counts of the attempt that failed stay part of the total.
     *
     * @param taskId    the task the step belongs to
     * @param loopIndex the loop index of the step, or null for a non-loop task
     */
    void forgetLastReported(String taskId, Integer loopIndex) {
        lastReported.remove(new StepKey(taskId, loopIndex));
    }

    /**
     * What one step has spent since the run began.
     *
     * @param taskId    the task the step belongs to
     * @param loopIndex the loop index of the step, or null for a non-loop task
     * @return the counts of the step, or null when the step has reported nothing
     */
    TokenUsage usageOfStep(String taskId, Integer loopIndex) {
        return accumulated.get(new StepKey(taskId, loopIndex));
    }

    /**
     * What a task has spent since the run began, over all of its steps. A loop task is the sum of
     * its indices.
     *
     * @param taskId the task
     * @return the counts of the task, or null when none of its steps has reported anything
     */
    TokenUsage usageOfTask(String taskId) {
        TokenUsage total = null;

        for (Map.Entry<StepKey, TokenUsage> entry : accumulated.entrySet()) {
            if (entry.getKey().taskId().equals(taskId)) {
                total = add(total, entry.getValue());
            }
        }

        return total;
    }

    int totalInputTokens() {
        return totalInputTokens;
    }

    int totalOutputTokens() {
        return totalOutputTokens;
    }

    int totalTokenCount() {
        return totalTokenCount;
    }

    /**
     * What the step has spent since the last value it reported. A value that is smaller than the
     * last one means that a new attempt started, so the whole value is new.
     */
    private static TokenUsage delta(TokenUsage previous, TokenUsage current) {
        if (previous == null) {
            return current;
        }

        if (isSmaller(current.inputTokenCount(), previous.inputTokenCount())
                || isSmaller(current.outputTokenCount(), previous.outputTokenCount())
                || isSmaller(current.totalTokenCount(), previous.totalTokenCount())) {
            // The aggregate went down, so a new attempt started and everything it reports is new.
            return current;
        }

        return new TokenUsage(deltaOf(current.inputTokenCount(), previous.inputTokenCount()),
                deltaOf(current.outputTokenCount(), previous.outputTokenCount()),
                deltaOf(current.totalTokenCount(), previous.totalTokenCount()));
    }

    private static Integer deltaOf(Integer current, Integer previous) {
        if (current == null) {
            return null;
        }

        return current - orZero(previous);
    }

    private static boolean isSmaller(Integer current, Integer previous) {
        return current != null && previous != null && current < previous;
    }

    private static TokenUsage add(TokenUsage left, TokenUsage right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        return new TokenUsage(addCounts(left.inputTokenCount(), right.inputTokenCount()),
                addCounts(left.outputTokenCount(), right.outputTokenCount()),
                addCounts(left.totalTokenCount(), right.totalTokenCount()));
    }

    private static Integer addCounts(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        return left + right;
    }

    private static int orZero(Integer count) {
        return count == null ? 0 : count;
    }
}
