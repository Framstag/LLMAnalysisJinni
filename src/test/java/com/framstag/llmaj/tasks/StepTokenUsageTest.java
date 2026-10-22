package com.framstag.llmaj.tasks;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The token counts of one task step: what a provider reported, and how the counts of several
 * attempts are added up.
 */
public class StepTokenUsageTest {

    @Test
    void theCountsOfAProviderResponseBecomeTheCountsOfTheStep() {
        StepTokenUsage usage = StepTokenUsage.from(new TokenUsage(10, 5, 15));

        assertNotNull(usage);
        assertEquals(10, usage.inputTokens());
        assertEquals(5, usage.outputTokens());
        assertEquals(15, usage.totalTokens());
        assertTrue(usage.isKnown());
    }

    @Test
    void aResponseWithoutCountsIsNoUsage() {
        assertNull(StepTokenUsage.from(null));
        assertNull(StepTokenUsage.from(new TokenUsage()),
                "a response that reports no count at all is no usage");
    }

    @Test
    void aResponseThatReportsOneCountIsAUsage() {
        StepTokenUsage usage = StepTokenUsage.from(new TokenUsage(10, null, null));

        assertNotNull(usage, "a count that is not reported is not a count of zero");
        assertEquals(10, usage.inputTokens());
        assertNull(usage.outputTokens());
        assertNull(usage.totalTokens());
    }

    @Test
    void twoUsagesAreAddedUp() {
        StepTokenUsage sum = StepTokenUsage.sum(new StepTokenUsage(10, 5, 15),
                new StepTokenUsage(1, 2, 3));

        assertEquals(11, sum.inputTokens());
        assertEquals(7, sum.outputTokens());
        assertEquals(18, sum.totalTokens());
    }

    @Test
    void aCountThatOneUsageDoesNotReportIsTakenFromTheOther() {
        StepTokenUsage sum = StepTokenUsage.sum(new StepTokenUsage(10, 5, null),
                new StepTokenUsage(1, null, 3));

        assertEquals(11, sum.inputTokens());
        assertEquals(5, sum.outputTokens(), "a count that one of them does not report is kept");
        assertEquals(3, sum.totalTokens());
    }

    @Test
    void summingWithAnUnknownUsageKeepsTheKnownOne() {
        StepTokenUsage known = new StepTokenUsage(10, 5, 15);

        assertSame(known, StepTokenUsage.sum(known, null));
        assertSame(known, StepTokenUsage.sum(null, known));
    }

    @Test
    void aCollectionOfUsagesIsAddedUp() {
        StepTokenUsage sum = StepTokenUsage.sum(List.of(
                new StepTokenUsage(10, 5, 15),
                new StepTokenUsage(1, 2, 3),
                new StepTokenUsage(100, 50, 150)));

        assertEquals(111, sum.inputTokens());
        assertEquals(57, sum.outputTokens());
        assertEquals(168, sum.totalTokens());
    }

    @Test
    void anEmptyCollectionHasNoUsage() {
        assertNull(StepTokenUsage.sum(List.of()));
    }
}
