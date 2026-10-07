package com.framstag.llmaj.tools.java.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four classes, one heavy tie and three light ones:
 *
 * <pre>
 *   A --1-- B
 *   |       |
 *   1       9
 *   |       |
 *   D --1-- C
 * </pre>
 */
class MaximumSpanningForestTest {
    static MapWeightedGraph fourNodeGraph() {
        MapWeightedGraph graph = new MapWeightedGraph();
        graph.addEdge("A", "B", 1);
        graph.addEdge("A", "D", 1);
        graph.addEdge("C", "D", 1);
        graph.addEdge("B", "C", 9);

        return graph;
    }

    @Test
    void theForestHoldsOneEdgeLessThanThereAreNodes() {
        List<GraphEdge> forest = MaximumSpanningForest.of(fourNodeGraph());

        assertEquals(3, forest.size(), "a connected graph of four nodes has a spanning tree of three edges");
    }

    @Test
    void theForestIsOrderedAscendingByWeight() {
        List<GraphEdge> forest = MaximumSpanningForest.of(fourNodeGraph());

        assertEquals(List.of(1, 1, 9), forest.stream().map(GraphEdge::weight).toList(),
                "the ladder walks the forest from the weakest tie upwards");
    }

    @Test
    void theHeavyTieIsPartOfTheForest() {
        List<GraphEdge> forest = MaximumSpanningForest.of(fourNodeGraph());

        assertEquals(new GraphEdge("B", "C", 9), forest.getLast(),
                "a maximum spanning forest keeps the heaviest tie");
    }

    @Test
    void theForestConnectsTheWholeGraph() {
        MapWeightedGraph forestOnly = new MapWeightedGraph();
        forestOnly.addNode("A");
        forestOnly.addNode("B");
        forestOnly.addNode("C");
        forestOnly.addNode("D");

        for (GraphEdge edge : MaximumSpanningForest.of(fourNodeGraph())) {
            forestOnly.addEdge(edge.from(), edge.to(), edge.weight());
        }

        assertEquals(1, ConnectedComponents.of(forestOnly).size(),
                "the forest has to reach every node of a connected graph");
    }

    @Test
    void aGraphWithoutEdgesHasAnEmptyForest() {
        MapWeightedGraph graph = new MapWeightedGraph();
        graph.addNode("A");
        graph.addNode("B");

        assertTrue(MaximumSpanningForest.of(graph).isEmpty());
    }

    @Test
    void anEdgeWithAnEndpointOrderThatIsReversedIsTheSameEdge() {
        assertEquals(new GraphEdge("A", "B", 3), new GraphEdge("B", "A", 3));
    }
}
