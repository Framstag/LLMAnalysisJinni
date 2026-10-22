package com.framstag.llmaj.display;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The token counts of a run: the engine reports the running aggregate of a step every time a model
 * answered, so the tracker must add only what a step has spent since its last report.
 */
public class TokenUsageTrackerTest {

    @Test
    void theFirstReportOfAStepIsItsWholeUsage() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", null, new TokenUsage(100, 10, 110));

        assertEquals(100, tracker.totalInputTokens());
        assertEquals(10, tracker.totalOutputTokens());
        assertEquals(110, tracker.totalTokenCount());
    }

    @Test
    void aFurtherReportOfTheSameStepAddsOnlyWhatIsNew() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", null, new TokenUsage(100, 10, 110));
        tracker.report("TaskA", null, new TokenUsage(250, 25, 275));

        assertEquals(250, tracker.totalInputTokens(),
                "the running aggregate must not be added on top of what it already contains");
        assertEquals(25, tracker.totalOutputTokens());
        assertEquals(275, tracker.totalTokenCount());
    }

    @Test
    void theTotalOfARunIsTheSumOfItsSteps() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", null, new TokenUsage(100, 10, 110));
        tracker.report("TaskB", null, new TokenUsage(200, 20, 220));

        assertEquals(300, tracker.totalInputTokens());
        assertEquals(330, tracker.totalTokenCount());
    }

    @Test
    void theIndicesOfALoopTaskAreStepsOfTheirOwn() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", 0, new TokenUsage(100, 10, 110));
        tracker.report("TaskA", 1, new TokenUsage(200, 20, 220));

        assertEquals(300, tracker.totalInputTokens());
        assertEquals(110, tracker.usageOfStep("TaskA", 0).totalTokenCount());
        assertEquals(220, tracker.usageOfStep("TaskA", 1).totalTokenCount());
    }

    @Test
    void theUsageOfATaskIsTheSumOfItsSteps() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", 0, new TokenUsage(100, 10, 110));
        tracker.report("TaskA", 1, new TokenUsage(200, 20, 220));

        TokenUsage taskUsage = tracker.usageOfTask("TaskA");

        assertNotNull(taskUsage);
        assertEquals(300, taskUsage.inputTokenCount());
        assertEquals(330, taskUsage.totalTokenCount());
    }

    @Test
    void aFurtherAttemptStartsANewAggregate() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", null, new TokenUsage(100, 10, 110));

        // The attempt failed and a further one starts, so the counts it reports are new.
        tracker.forgetLastReported("TaskA", null);
        tracker.report("TaskA", null, new TokenUsage(50, 5, 55));

        assertEquals(150, tracker.totalInputTokens(),
                "the counts of the attempt that failed stay part of the total");
        assertEquals(165, tracker.totalTokenCount());
    }

    @Test
    void anAggregateThatGoesDownIsTreatedAsANewAttempt() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", null, new TokenUsage(100, 10, 110));
        tracker.report("TaskA", null, new TokenUsage(50, 5, 55));

        assertEquals(150, tracker.totalInputTokens(),
                "a smaller aggregate means a new attempt, so all of it is new");
    }

    @Test
    void aStepThatReportedNothingContributesNothing() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", null, null);

        assertEquals(0, tracker.totalTokenCount());
        assertNull(tracker.usageOfStep("TaskA", null));
    }

    @Test
    void aCountThatIsNotReportedIsNotACountOfZero() {
        TokenUsageTracker tracker = new TokenUsageTracker();

        tracker.report("TaskA", null, new TokenUsage(100, null, null));

        assertEquals(100, tracker.totalInputTokens());
        assertEquals(0, tracker.totalOutputTokens(), "a count that is not reported adds nothing");
        assertNull(tracker.usageOfStep("TaskA", null).outputTokenCount(),
                "a count that is not reported stays unreported");
    }
}
