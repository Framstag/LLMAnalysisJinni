package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Collection;

/**
 * The token counts of one task step: a non-loop task, or one loop index of a loop task.
 * <p>
 * The counts are the ones the model provider reported. They are nullable, because a provider may
 * report no count for a kind of token, and a count that is not known is not a count of zero.
 * <p>
 * A step carries the counts of the attempts of its last execution, so a step that was attempted
 * more than once reports what it really cost. The attempts themselves are not kept.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepTokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {

    /**
     * The counts of a provider response, or null when the provider reported no count at all.
     *
     * @param usage the counts the provider reported, or null
     * @return the counts of the step, or null when nothing is known
     */
    public static StepTokenUsage from(TokenUsage usage) {
        if (usage == null) {
            return null;
        }

        StepTokenUsage result = new StepTokenUsage(usage.inputTokenCount(),
                usage.outputTokenCount(),
                usage.totalTokenCount());

        return result.isKnown() ? result : null;
    }

    /**
     * The sum of two usages. A count that one of them does not report is taken from the other one.
     *
     * @param left  the first usage, or null
     * @param right the second usage, or null
     * @return the sum, or null when neither usage is known
     */
    public static StepTokenUsage sum(StepTokenUsage left, StepTokenUsage right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        return new StepTokenUsage(add(left.inputTokens(), right.inputTokens()),
                add(left.outputTokens(), right.outputTokens()),
                add(left.totalTokens(), right.totalTokens()));
    }

    /**
     * The sum of a collection of usages, or null when none of them reports a count.
     *
     * @param usages the usages to add up
     * @return the sum, or null when no usage is known
     */
    public static StepTokenUsage sum(Collection<StepTokenUsage> usages) {
        StepTokenUsage total = null;

        for (StepTokenUsage usage : usages) {
            total = sum(total, usage);
        }

        return total;
    }

    /**
     * True when at least one count is known. A usage that reports nothing is no usage at all.
     */
    @JsonIgnore
    public boolean isKnown() {
        return inputTokens != null || outputTokens != null || totalTokens != null;
    }

    private static Integer add(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        return left + right;
    }
}
