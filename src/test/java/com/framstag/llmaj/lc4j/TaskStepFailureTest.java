package com.framstag.llmaj.lc4j;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The text a rejected attempt produces: one bounded line for the display, and the bounded repair
 * hint a further attempt is given.
 */
public class TaskStepFailureTest {

    @Test
    void displayMessageNamesTheReasonAndTheDetail() {
        TaskStepFailure failure = TaskStepFailure.of(StepFailureReason.NO_PAYLOAD,
                "no JSON payload found in the response");

        String display = failure.displayMessage();

        assertTrue(display.contains("no JSON payload in the response"),
                "the display must name the reason, got: " + display);
        assertTrue(display.contains("no JSON payload found in the response"),
                "the display must carry the detail, got: " + display);
        assertFalse(display.contains("\n"), "the display shares one line with the engine log");
    }

    @Test
    void displayMessageIsBounded() {
        TaskStepFailure failure = TaskStepFailure.of(StepFailureReason.NO_PAYLOAD, "x".repeat(5000));

        assertTrue(failure.displayMessage().length() <= TaskStepFailure.MAX_DISPLAY_LENGTH,
                "a long detail must not grow the reserved display line");
        assertTrue(failure.displayMessage().endsWith("..."));
    }

    @Test
    void repairHintNamesTheReason() {
        TaskStepFailure failure = TaskStepFailure.of(StepFailureReason.NO_RESPONSE,
                "the model returned no response text");

        String hint = failure.repairHint();

        assertTrue(hint.contains("rejected"));
        assertTrue(hint.contains("no response text"));
        assertTrue(hint.contains("JSON schema"),
                "the hint must point at the schema the answer has to match, got: " + hint);
    }

    @Test
    void repairHintListsTheViolations() {
        TaskStepFailure failure = TaskStepFailure.schemaViolation(List.of(
                "$.modules[0].purpose: required property 'purpose' not found",
                "$.summary: expected type string, found integer"));

        String hint = failure.repairHint();

        assertTrue(hint.contains("schema violation"));
        assertTrue(hint.contains("$.modules[0].purpose: required property 'purpose' not found"));
        assertTrue(hint.contains("$.summary: expected type string, found integer"));
    }

    @Test
    void repairHintCarriesTheExcerptOfARejectedPayload() {
        TaskStepFailure failure = TaskStepFailure.of(StepFailureReason.PAYLOAD_NOT_PARSEABLE,
                "JSON payload found in the response is not parseable", "{\"answer\": ");

        String hint = failure.repairHint();

        assertTrue(hint.contains("does not parse"));
        assertTrue(hint.contains("{\"answer\": "));
    }

    @Test
    void repairHintSummarisesViolationsBeyondTheListLimit() {
        List<String> violations = new ArrayList<>();

        for (int i = 0; i < TaskStepFailure.MAX_HINT_VIOLATIONS + 4; i++) {
            violations.add("$.field" + i + ": violation number " + i);
        }

        String hint = TaskStepFailure.schemaViolation(violations).repairHint();

        assertTrue(hint.contains("$.field0: violation number 0"), "the first violation must be listed");
        assertFalse(hint.contains("$.field" + violations.size() + ": violation"),
                "the hint must not list violations beyond the limit");
        assertTrue(hint.contains("further violation"), "the rest must be summarised, got: " + hint);
    }

    @Test
    void repairHintIsBounded() {
        List<String> violations = new ArrayList<>();

        for (int i = 0; i < TaskStepFailure.MAX_HINT_VIOLATIONS; i++) {
            violations.add("$.field" + i + ": " + "y".repeat(400));
        }

        String hint = TaskStepFailure.schemaViolation(violations).repairHint();

        assertTrue(hint.length() <= TaskStepFailure.MAX_HINT_LENGTH,
                "a hint is injected into a prompt and must stay bounded, was " + hint.length());
    }

    @Test
    void toolRoundBoundRejectionNamesTheBound() {
        TaskStepFailure failure = TaskStepFailure.of(StepFailureReason.TOOL_ROUND_TRIPS_EXCEEDED,
                "the model requested more than 10 tool rounds in one attempt");

        String display = failure.displayMessage();
        String hint = failure.repairHint();

        assertTrue(display.contains("tool round bound exceeded"),
                "the display must name the reason, got: " + display);
        assertTrue(display.contains("more than 10 tool rounds"),
                "the display must name the bound, got: " + display);
        assertTrue(hint.contains("tool round bound exceeded"),
                "the next attempt must be told the reason, got: " + hint);
        assertTrue(hint.contains("more than 10 tool rounds"),
                "the next attempt must be told the bound, got: " + hint);
    }
}
