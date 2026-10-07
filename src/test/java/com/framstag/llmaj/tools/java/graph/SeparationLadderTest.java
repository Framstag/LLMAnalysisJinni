package com.framstag.llmaj.tools.java.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ladder is the answer to "where does this module come apart". It is ordered by ascending cost, so the
 * cheapest separation is the first entry, and the cost of a step is the strongest tie between the groups it
 * separates: one heavy tie means the two sides are not independent, however few ties there are.
 */
class SeparationLadderTest {
    @Test
    void theLadderRisesInCost() {
        List<SeparationLadder.Separation> ladder = SeparationLadder.of(MaximumSpanningForestTest.fourNodeGraph());

        assertEquals(List.of(1, 9), ladder.stream().map(SeparationLadder.Separation::cost).toList(),
                "an ascending ladder starts with the cheapest separation");
    }

    @Test
    void aLightTieSeparatesIntoThreeGroups() {
        SeparationLadder.Separation separation = SeparationLadder.of(MaximumSpanningForestTest.fourNodeGraph())
                .getFirst();

        assertEquals(3, separation.groups().size());
        assertEquals(List.of(Set.of("A"), Set.of("B", "C"), Set.of("D")), separation.groups());
    }

    @Test
    void aHeavyTieIsSeparatedLastAndCostsItsWeight() {
        SeparationLadder.Separation separation = SeparationLadder.of(MaximumSpanningForestTest.fourNodeGraph())
                .getLast();

        assertEquals(9, separation.cost(),
                "the strongest tie between the groups that came apart is what the separation costs");
        assertEquals(4, separation.groups().size());
    }

    @Test
    void aHeavyTieDoesNotHideBehindLightOnes() {
        SeparationLadder.Separation separation = SeparationLadder.of(MaximumSpanningForestTest.fourNodeGraph())
                .getLast();

        assertEquals(9, separation.strongestSeveredWeight(),
                "the heavy tie between B and C is what the last step severs");
        assertEquals(1, separation.weakestSeveredWeight(),
                "the light ties that are already severed are still reported");
        assertEquals(12, separation.totalSeveredWeight(),
                "1 + 1 + 1 + 9 is the total weight given up to break the graph apart");
    }

    @Test
    void theLadderIsDeterministic() {
        assertEquals(SeparationLadder.of(MaximumSpanningForestTest.fourNodeGraph()),
                SeparationLadder.of(MaximumSpanningForestTest.fourNodeGraph()),
                "the ladder needs no seed and no resolution parameter, so two runs agree");
    }

    @Test
    void aGraphWithoutEdgesHasNoLadder() {
        MapWeightedGraph graph = new MapWeightedGraph();
        graph.addNode("A");
        graph.addNode("B");

        assertTrue(SeparationLadder.of(graph).isEmpty(),
                "classes that never reference each other are already separate, there is nothing to propose");
    }

    @Test
    void aSingleNodeHasNoLadder() {
        MapWeightedGraph graph = new MapWeightedGraph();
        graph.addNode("A");

        assertTrue(SeparationLadder.of(graph).isEmpty());
    }

    @Test
    void everyStepAddsAtLeastOneGroup() {
        List<SeparationLadder.Separation> ladder = SeparationLadder.of(MaximumSpanningForestTest.fourNodeGraph());

        int previousGroupCount = 1;
        for (SeparationLadder.Separation separation : ladder) {
            assertTrue(separation.groups().size() > previousGroupCount,
                    "a step that does not change the partition is not a candidate separation");
            previousGroupCount = separation.groups().size();
        }
    }
}
