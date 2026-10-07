package com.framstag.llmaj.state;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The loop target of one loop task execution.
 * <p>
 * A cursor is created for exactly one execution and is dropped when that execution ends. It is not
 * shared state: two loop tasks can hold a cursor over the same analysis array at the same time, which
 * is what lets them execute concurrently instead of failing with "Loop already started".
 * <p>
 * The cursor holds the array node of the analysis state, so writes through it are visible in the state
 * the run publishes. Writing through a cursor must stay inside {@link StateManager}, which serializes
 * every state mutation.
 */
public final class LoopCursor {

    private final String loopOn;
    private final JsonNode entries;

    LoopCursor(String loopOn, JsonNode entries) {
        this.loopOn = loopOn;
        this.entries = entries;
    }

    /**
     * The JSON path the loop was started on, for diagnostics.
     */
    public String getLoopOn() {
        return loopOn;
    }

    /**
     * Number of entries the loop iterates over, taken when the cursor was created.
     */
    public int size() {
        return entries.size();
    }

    /**
     * The entry of one loop index.
     */
    public JsonNode at(int index) {
        return entries.get(index);
    }
}
