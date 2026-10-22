package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.framstag.llmaj.tasks.StepTokenUsage;

/**
 * The result of one attempt of a task step: either an accepted payload, or the reason the attempt
 * was rejected.
 * <p>
 * A rejected attempt is an expected outcome, not an error, so it is returned instead of thrown.
 * Only {@link #isAccepted()} outcomes may be published and stored.
 * <p>
 * The outcome also carries the token usage of the attempt, whether the attempt was accepted or
 * rejected: a rejected attempt cost tokens as well. The retry driver sums the usages of the
 * attempts of a step into the usage of that step.
 */
public record TaskStepOutcome(JsonNode payload, TaskStepFailure failure, StepTokenUsage tokenUsage) {

    public TaskStepOutcome {
        if ((payload == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of payload and failure must be set");
        }
    }

    public TaskStepOutcome(JsonNode payload, TaskStepFailure failure) {
        this(payload, failure, null);
    }

    public static TaskStepOutcome accepted(JsonNode payload) {
        return new TaskStepOutcome(payload, null, null);
    }

    public static TaskStepOutcome accepted(JsonNode payload, StepTokenUsage tokenUsage) {
        return new TaskStepOutcome(payload, null, tokenUsage);
    }

    public static TaskStepOutcome rejected(TaskStepFailure failure) {
        return new TaskStepOutcome(null, failure, null);
    }

    public static TaskStepOutcome rejected(TaskStepFailure failure, StepTokenUsage tokenUsage) {
        return new TaskStepOutcome(null, failure, tokenUsage);
    }

    public boolean isAccepted() {
        return payload != null;
    }

    /**
     * The same outcome with another token usage, used by the retry driver to replace the usage of
     * the last attempt by the usage of all attempts of the step.
     *
     * @param tokenUsage the usage the outcome shall carry
     * @return the outcome carrying that usage
     */
    public TaskStepOutcome withTokenUsage(StepTokenUsage tokenUsage) {
        return new TaskStepOutcome(payload, failure, tokenUsage);
    }
}
