package com.framstag.llmaj.tools.java.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A god class ranking compares a class with its peers in the same module. Nothing here is a threshold, so the
 * interesting cases are ties, the extremes, and the module that holds too few classes to compare at all.
 */
class PercentilesTest {
    @Test
    void theSmallestValueIsTheLowestPercentile() {
        assertEquals(0, Percentiles.of(List.of(1.0, 2.0, 3.0, 4.0), 1.0));
    }

    @Test
    void theLargestValueIsTheHighestPercentile() {
        assertEquals(75, Percentiles.of(List.of(1.0, 2.0, 3.0, 4.0), 4.0),
                "three of four peers are exceeded");
    }

    @Test
    void aValueInTheMiddleCountsOnlyThePeersItExceeds() {
        assertEquals(50, Percentiles.of(List.of(1.0, 2.0, 3.0, 4.0), 3.0));
    }

    @Test
    void tiedValuesShareThePercentile() {
        List<Double> values = List.of(5.0, 5.0, 5.0, 9.0);

        assertEquals(0, Percentiles.of(values, 5.0),
                "a value that no peer is below does not stand out, however many peers tie with it");
        assertEquals(75, Percentiles.of(values, 9.0));
    }

    @Test
    void aSetOfOneHasNoPeerToStandOutFrom() {
        assertEquals(0, Percentiles.of(List.of(7.0), 7.0),
                "with no peer to compare against, a class must not be reported as standing out");
    }

    @Test
    void anEmptySetYieldsTheLowestPercentile() {
        assertEquals(0, Percentiles.of(List.of(), 7.0));
    }
}
