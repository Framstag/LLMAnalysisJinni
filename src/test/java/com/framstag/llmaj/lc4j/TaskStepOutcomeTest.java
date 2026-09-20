package com.framstag.llmaj.lc4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The outcome of one attempt: either an accepted payload or the reason the attempt was rejected,
 * never both and never neither.
 */
public class TaskStepOutcomeTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptedOutcomeCarriesThePayload() throws Exception {
        JsonNode payload = mapper.readTree("{\"answer\":\"ok\"}");

        TaskStepOutcome outcome = TaskStepOutcome.accepted(payload);

        assertTrue(outcome.isAccepted());
        assertSame(payload, outcome.payload());
        assertNull(outcome.failure());
    }

    @Test
    void rejectedOutcomeCarriesTheFailure() {
        TaskStepFailure failure = TaskStepFailure.of(StepFailureReason.NO_RESPONSE,
                "the model returned no response text");

        TaskStepOutcome outcome = TaskStepOutcome.rejected(failure);

        assertFalse(outcome.isAccepted());
        assertSame(failure, outcome.failure());
        assertNull(outcome.payload());
    }

    @Test
    void outcomeRefusesBothPayloadAndFailure() throws Exception {
        JsonNode payload = mapper.readTree("{}");

        assertThrows(IllegalArgumentException.class,
                () -> new TaskStepOutcome(payload, TaskStepFailure.of(StepFailureReason.NO_RESPONSE, "x")));
    }

    @Test
    void outcomeRefusesNeitherPayloadNorFailure() {
        assertThrows(IllegalArgumentException.class, () -> new TaskStepOutcome(null, null));
    }

    @Test
    void everyReasonKeepsItsPayload() {
        TaskStepFailure noResponse = TaskStepFailure.of(StepFailureReason.NO_RESPONSE, "no text");
        TaskStepFailure noPayload = TaskStepFailure.of(StepFailureReason.NO_PAYLOAD, "prose only");
        TaskStepFailure unparseable = TaskStepFailure.of(StepFailureReason.PAYLOAD_NOT_PARSEABLE,
                "not parseable", "{\"answer\":");
        TaskStepFailure violation = TaskStepFailure.schemaViolation(List.of("$.answer: required property missing"));
        TaskStepFailure modelError = TaskStepFailure.of(StepFailureReason.RETRIABLE_MODEL_ERROR, "timed out");

        assertEquals(StepFailureReason.NO_RESPONSE, noResponse.reason());
        assertEquals(StepFailureReason.NO_PAYLOAD, noPayload.reason());
        assertEquals(StepFailureReason.PAYLOAD_NOT_PARSEABLE, unparseable.reason());
        assertEquals(StepFailureReason.SCHEMA_VIOLATION, violation.reason());
        assertEquals(StepFailureReason.RETRIABLE_MODEL_ERROR, modelError.reason());

        assertEquals("{\"answer\":", unparseable.excerpt());
        assertEquals(List.of("$.answer: required property missing"), violation.violationMessages());
        assertTrue(noPayload.violationMessages().isEmpty());
        assertNull(noPayload.excerpt());
    }

    @Test
    void aFailureWithoutViolationsStillReportsThemAsAnEmptyList() {
        TaskStepFailure failure = new TaskStepFailure(StepFailureReason.NO_PAYLOAD, "prose only", null, null);

        assertNotNull(failure.violationMessages());
        assertTrue(failure.violationMessages().isEmpty());
    }
}
