package com.framstag.llmaj.tools.java.graph;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minimum cut is the secondary figure of a separation: the least total coupling that has to be given up to
 * break the graph in two. It has to agree with the graph it was computed from, including on a graph that is
 * already in pieces.
 */
class MinimumCutTest {
    private static int weightOfCut(WeightedGraph graph, Set<String> side) {
        int weight = 0;

        for (GraphEdge edge : graph.edges()) {
            if (side.contains(edge.from()) != side.contains(edge.to())) {
                weight += edge.weight();
            }
        }

        return weight;
    }

    @Test
    void aDisconnectedGraphCostsNothingToSplit() {
        MapWeightedGraph graph = new MapWeightedGraph();
        graph.addEdge("A", "B", 5);
        graph.addNode("C");

        Optional<MinimumCut.Cut> cut = MinimumCut.of(graph);

        assertTrue(cut.isPresent());
        assertEquals(0, cut.get().weight(),
                "an isolated class is already separate, so separating it costs no coupling");
        assertEquals(Set.of("C"), cut.get().side());
    }

    @Test
    void theCheapestCutIsFound() {
        MinimumCut.Cut cut = MinimumCut.of(MaximumSpanningForestTest.fourNodeGraph()).orElseThrow();

        assertEquals(2, cut.weight());
        assertEquals(cut.weight(), weightOfCut(MaximumSpanningForestTest.fourNodeGraph(), cut.side()),
                "the reported weight has to be the weight of the reported side");
    }

    @Test
    void oneOfSeveralEqualCutsIsFound() {
        MapWeightedGraph graph = new MapWeightedGraph();
        graph.addEdge("A", "B", 1);
        graph.addEdge("B", "C", 1);
        graph.addEdge("C", "A", 1);
        graph.addEdge("D", "E", 1);
        graph.addEdge("E", "F", 1);
        graph.addEdge("F", "D", 1);
        graph.addEdge("C", "D", 10);

        MinimumCut.Cut cut = MinimumCut.of(graph).orElseThrow();

        assertEquals(2, cut.weight());
        assertEquals(cut.weight(), weightOfCut(graph, cut.side()),
                "the reported weight has to be the weight of the reported side");

        assertEquals(2, weightOfCut(graph, Set.of("A")),
                "the graph holds more than one cut of the reported cost, so the result is one of several");
        assertEquals(2, weightOfCut(graph, Set.of("B")));
    }

    @Test
    void aSingleNodeHasNoCut() {
        MapWeightedGraph graph = new MapWeightedGraph();
        graph.addNode("A");

        assertTrue(MinimumCut.of(graph).isEmpty());
    }

    @Test
    void anEmptyGraphHasNoCut() {
        assertTrue(MinimumCut.of(new MapWeightedGraph()).isEmpty());
    }

    @Test
    void theCutIsDeterministic() {
        assertEquals(MinimumCut.of(MaximumSpanningForestTest.fourNodeGraph()),
                MinimumCut.of(MaximumSpanningForestTest.fourNodeGraph()));
    }
}
