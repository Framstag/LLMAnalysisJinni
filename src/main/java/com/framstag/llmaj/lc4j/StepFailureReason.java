package com.framstag.llmaj.lc4j;

/**
 * Why one attempt of a task step did not produce an acceptable answer.
 * <p>
 * Every reason except {@link #RETRIABLE_MODEL_ERROR} is produced by the response handling itself;
 * {@link #RETRIABLE_MODEL_ERROR} is produced when the model call raised an error that another
 * attempt may get past.
 */
public enum StepFailureReason {
    /** The model returned no response text at all. */
    NO_RESPONSE("no response text"),
    /** The response carries no locatable JSON payload. */
    NO_PAYLOAD("no JSON payload in the response"),
    /** A JSON payload was located, but it does not parse. */
    PAYLOAD_NOT_PARSEABLE("the located JSON payload does not parse"),
    /** The response parses, but it does not conform to the declared response schema. */
    SCHEMA_VIOLATION("schema violation"),
    /** The model call failed with an error another attempt may get past. */
    RETRIABLE_MODEL_ERROR("retriable model error");

    private final String label;

    StepFailureReason(String label) {
        this.label = label;
    }

    /**
     * Short human-readable name of the reason, used in the display, the engine log and the repair
     * hint given to the next attempt.
     */
    public String label() {
        return label;
    }
}
