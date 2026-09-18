package com.framstag.llmaj.json;

import java.io.IOException;

/**
 * Raised when a model response does not yield a JSON payload that parses.
 * <p>
 * Carries the condition, so a caller can tell a response that never had a payload from one whose
 * payload is malformed, and a bounded excerpt instead of the whole response body.
 */
public class ResponsePayloadException extends IOException {

    /**
     * What went wrong, so a caller can report the two cases differently.
     */
    public enum Condition {
        /** No bracketed JSON payload was found in the response at all. */
        NO_PAYLOAD_LOCATED,
        /** A payload was located, but it is not syntactically valid JSON. */
        PAYLOAD_NOT_PARSEABLE
    }

    private final Condition condition;
    private final String excerpt;
    private final int responseLength;

    public ResponsePayloadException(Condition condition,
                                    String message,
                                    String excerpt,
                                    int responseLength) {
        super(message);
        this.condition = condition;
        this.excerpt = excerpt;
        this.responseLength = responseLength;
    }

    public Condition getCondition() {
        return condition;
    }

    /**
     * The beginning of the response, cut to a bounded length.
     */
    public String getExcerpt() {
        return excerpt;
    }

    /**
     * Length of the whole response, so the excerpt is recognizable as a fragment.
     */
    public int getResponseLength() {
        return responseLength;
    }
}
