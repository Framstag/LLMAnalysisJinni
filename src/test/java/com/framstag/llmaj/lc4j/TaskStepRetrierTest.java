package com.framstag.llmaj.lc4j;

import com.framstag.llmaj.display.ProgressCallback;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The attempts of one task step: how often a rejected answer is attempted again, which errors end
 * the step immediately, and what a further attempt is told.
 */
public class TaskStepRetrierTest {

    /**
     * Records what the display is told about the retries.
     */
    private static final class RecordingCallback implements ProgressCallback {
        private final List<String> retries = new ArrayList<>();
        private int completions;

        @Override
        public void onRetry(String taskId, Integer loopIndex, int attempt, int maxAttempts, String reason) {
            retries.add(taskId + "[" + (loopIndex == null ? "-" : loopIndex) + "] " + attempt + "/"
                    + maxAttempts + " " + reason);
        }

        @Override
        public void onComplete(String taskId, Integer loopIndex) {
            completions++;
        }

        @Override public void onRequestSent(String taskId, Integer loopIndex) { }
        @Override public void onResponseReceived(String taskId, Integer loopIndex) { }
        @Override public void onToolCall(String taskId, Integer loopIndex, String toolName) { }
        @Override public void onToolResult(String taskId, Integer loopIndex, String toolName) { }
        @Override public void onTokenUsage(String taskId, Integer loopIndex, TokenUsage tokenUsage) { }
        @Override public void onError(String taskId, Integer loopIndex, String errorMessage) { }
    }

    /**
     * An attempt that rejects its answers until its script says otherwise.
     */
    private static final class ScriptedAttempt implements TaskStepRetrier.StepAttempt {
        private final List<TaskStepOutcome> outcomes;
        private final List<String> repairHints = new ArrayList<>();
        private int calls;

        ScriptedAttempt(TaskStepOutcome... outcomes) {
            this.outcomes = List.of(outcomes);
        }

        @Override
        public TaskStepOutcome attempt(int attemptNumber, String repairHint) {
            calls++;
            repairHints.add(repairHint);

            return outcomes.get(Math.min(calls - 1, outcomes.size() - 1));
        }

        int calls() {
            return calls;
        }

        List<String> repairHints() {
            return repairHints;
        }
    }

    private static TaskStepOutcome rejected(StepFailureReason reason, String message) {
        return TaskStepOutcome.rejected(TaskStepFailure.of(reason, message));
    }

    @Test
    void aStepFailingTwiceWithBudgetThreeRunsThreeAttempts() throws IOException {
        RecordingCallback callback = new RecordingCallback();
        ScriptedAttempt attempt = new ScriptedAttempt(rejected(StepFailureReason.NO_PAYLOAD, "prose only"));

        TaskStepOutcome outcome = new TaskStepRetrier(3, callback, "TestTask", null).run(attempt);

        assertEquals(3, attempt.calls(), "a budget of 3 means three attempts in total");
        assertFalse(outcome.isAccepted());
        assertEquals(StepFailureReason.NO_PAYLOAD, outcome.failure().reason());
        assertEquals(2, callback.retries.size(), "only the exhausted attempts before the last one are retries");
        assertEquals(0, callback.completions, "a step without an accepted answer is not complete");
    }

    @Test
    void anAcceptedAttemptStopsTheRetrying() throws IOException {
        RecordingCallback callback = new RecordingCallback();
        ScriptedAttempt attempt = new ScriptedAttempt(
                rejected(StepFailureReason.SCHEMA_VIOLATION, "wrong type"),
                TaskStepOutcome.accepted(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("ok")));

        TaskStepOutcome outcome = new TaskStepRetrier(3, callback, "TestTask", null).run(attempt);

        assertEquals(2, attempt.calls(), "an accepted answer must not be followed by another attempt");
        assertTrue(outcome.isAccepted());
        assertEquals(1, callback.retries.size());
        assertEquals(1, callback.completions, "the accepted attempt completes the step");
    }

    @Test
    void aRetriableModelErrorIsAttemptedAgain() throws IOException {
        RecordingCallback callback = new RecordingCallback();
        TaskStepRetrier retrier = new TaskStepRetrier(3, callback, "TestTask", 7);

        int[] calls = {0};

        TaskStepOutcome outcome = retrier.run((attemptNumber, repairHint) -> {
            calls[0]++;
            throw new TimeoutException("the model did not answer in time");
        });

        assertEquals(3, calls[0], "a timeout may pass on another attempt");
        assertFalse(outcome.isAccepted());
        assertEquals(StepFailureReason.RETRIABLE_MODEL_ERROR, outcome.failure().reason());
        assertTrue(callback.retries.getFirst().contains("[7] 1/3"),
                "the retry must name the loop index, got: " + callback.retries.getFirst());
    }

    @Test
    void aNonRetriableModelErrorEndsTheStepWithoutAnotherAttempt() {
        int[] calls = {0};

        TaskStepRetrier retrier = new TaskStepRetrier(3, new RecordingCallback(), "TestTask", null);

        assertThrows(AuthenticationException.class, () -> retrier.run((attemptNumber, repairHint) -> {
            calls[0]++;
            throw new AuthenticationException("the api key was rejected");
        }));

        assertEquals(1, calls[0], "an authentication failure cannot be repaired by trying again");
    }

    @Test
    void anotherNonRetriableErrorAlsoEndsTheStep() {
        int[] calls = {0};

        TaskStepRetrier retrier = new TaskStepRetrier(3, new RecordingCallback(), "TestTask", null);

        assertThrows(NonRetriableException.class, () -> retrier.run((attemptNumber, repairHint) -> {
            calls[0]++;
            throw new NonRetriableException("the model does not exist");
        }));

        assertEquals(1, calls[0]);
    }

    @Test
    void anEngineFailureEndsTheStepWithoutAnotherAttempt() {
        int[] calls = {0};

        TaskStepRetrier retrier = new TaskStepRetrier(3, new RecordingCallback(), "TestTask", null);

        IOException exception = assertThrows(IOException.class, () -> retrier.run((attemptNumber, repairHint) -> {
            calls[0]++;
            throw new IOException("the chat log cannot be written");
        }));

        assertEquals(1, calls[0], "another attempt cannot repair a disk problem");
        assertEquals("the chat log cannot be written", exception.getMessage());
    }

    @Test
    void theNextAttemptIsToldWhatWasWrong() throws IOException {
        ScriptedAttempt attempt = new ScriptedAttempt(
                TaskStepOutcome.rejected(TaskStepFailure.schemaViolation(
                        List.of("$.answer: expected type string, found integer"))),
                TaskStepOutcome.rejected(TaskStepFailure.schemaViolation(
                        List.of("$.answer: expected type string, found integer"))),
                TaskStepOutcome.accepted(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("ok")));

        new TaskStepRetrier(3, new RecordingCallback(), "TestTask", null).run(attempt);

        assertNull(attempt.repairHints().getFirst(), "the first attempt has nothing to be told");
        assertNotNull(attempt.repairHints().get(1), "the second attempt must be told what was wrong");
        assertTrue(attempt.repairHints().get(1).contains("$.answer: expected type string, found integer"),
                "the violations of the previous attempt must reach the next one");
        assertTrue(attempt.repairHints().get(2).contains("$.answer: expected type string, found integer"));
    }

    @Test
    void aBudgetOfOneAttemptsTheStepOnce() throws IOException {
        RecordingCallback callback = new RecordingCallback();
        ScriptedAttempt attempt = new ScriptedAttempt(rejected(StepFailureReason.NO_RESPONSE, "empty"));

        TaskStepOutcome outcome = new TaskStepRetrier(1, callback, "TestTask", null).run(attempt);

        assertEquals(1, attempt.calls(), "a budget of 1 disables the retrying");
        assertFalse(outcome.isAccepted());
        assertTrue(callback.retries.isEmpty());
    }

    @Test
    void aBudgetBelowOneStillAttemptsTheStepOnce() throws IOException {
        ScriptedAttempt attempt = new ScriptedAttempt(TaskStepOutcome.accepted(
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("ok")));

        new TaskStepRetrier(0, new RecordingCallback(), "TestTask", null).run(attempt);

        assertEquals(1, attempt.calls(), "a step is always attempted at least once");
    }

    @Test
    void withoutACallbackTheRetryingStillWorks() throws IOException {
        ScriptedAttempt attempt = new ScriptedAttempt(rejected(StepFailureReason.NO_PAYLOAD, "prose only"));

        TaskStepOutcome outcome = new TaskStepRetrier(2, null, "TestTask", null).run(attempt);

        assertEquals(2, attempt.calls());
        assertFalse(outcome.isAccepted());
    }

    @Test
    void everyRetryEmitsAWarnRecordNamingTheTask() throws IOException {
        ch.qos.logback.classic.Logger retrierLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(TaskStepRetrier.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        retrierLogger.addAppender(appender);

        try {
            ScriptedAttempt attempt = new ScriptedAttempt(
                    TaskStepOutcome.rejected(TaskStepFailure.schemaViolation(List.of("$.answer: not a string"))),
                    TaskStepOutcome.rejected(TaskStepFailure.schemaViolation(List.of("$.answer: not a string"))),
                    TaskStepOutcome.accepted(
                            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("ok")));

            new TaskStepRetrier(3, new RecordingCallback(), "TestTask", 7).run(attempt);

            List<String> warnings = appender.list.stream()
                    .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .map(event -> event.getFormattedMessage())
                    .toList();

            assertEquals(2, warnings.size(),
                    "one record per retry is what the reserved display line and the engine log show");
            assertTrue(warnings.getFirst().contains("TestTask"),
                    "the record must name the task, got: " + warnings.getFirst());
            assertTrue(warnings.getFirst().contains("1/3"),
                    "the record must name the attempt, got: " + warnings.getFirst());
            assertTrue(warnings.getFirst().contains("schema violation"),
                    "the record must carry the reason, got: " + warnings.getFirst());
            assertTrue(warnings.getFirst().contains("[index 7]"),
                    "the record must name the loop index, got: " + warnings.getFirst());
            assertTrue(warnings.getFirst().length() <= TaskStepFailure.MAX_DISPLAY_LENGTH + 100,
                    "the record must stay bounded, was " + warnings.getFirst().length());
        } finally {
            retrierLogger.detachAppender(appender);
        }
    }

    @Test
    void attemptsAreSequentialOnTheCallingThread() throws IOException {
        List<String> threads = new ArrayList<>();

        new TaskStepRetrier(3, new RecordingCallback(), "TestTask", null).run((attemptNumber, repairHint) -> {
            threads.add(Thread.currentThread().getName());

            return rejected(StepFailureReason.NO_PAYLOAD, "prose only");
        });

        assertEquals(1, threads.stream().distinct().count(),
                "all attempts of a step run on the thread that owns the step, got: " + threads);
    }
}
