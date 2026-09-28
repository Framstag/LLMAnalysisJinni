package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The result of one attempt of a task step: either an accepted payload, or the reason the attempt
 * was rejected.
 * <p>
 * A rejected attempt is an expected outcome, not an error, so it is returned instead of thrown.
 * Only {@link #isAccepted()} outcomes may be published and stored.
 */
public record TaskStepOutcome(JsonNode payload, TaskStepFailure failure) {

    public TaskStepOutcome {
        if ((payload == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of payload and failure must be set");
        }
    }

    public static TaskStepOutcome accepted(JsonNode payload) {
        return new TaskStepOutcome(payload, null);
    }

    public static TaskStepOutcome rejected(TaskStepFailure failure) {
        return new TaskStepOutcome(null, failure);
    }

    public boolean isAccepted() {
        return payload != null;
    }
}
