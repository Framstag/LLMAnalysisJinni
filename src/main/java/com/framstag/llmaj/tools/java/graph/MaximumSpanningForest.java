package com.framstag.llmaj.tools.java.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kruskal's maximum spanning forest.
 *
 * <p>A maximum spanning forest has the property the separation ladder rests on: for every threshold, the
 * components of the graph that keep only the edges above the threshold are exactly the components of the
 * forest that keep only its edges above the threshold. That is what makes the whole split hierarchy readable
 * from one pass over the forest instead of one pass per threshold.
 */
public final class MaximumSpanningForest {
    private MaximumSpanningForest() {
    }

    /**
     * @return the forest edges in ascending weight order, which is the order the separation ladder walks them
     */
    public static List<GraphEdge> of(WeightedGraph graph) {
        List<GraphEdge> candidates = new ArrayList<>(graph.edges());
        candidates.sort(Comparator.comparingInt(GraphEdge::weight).reversed().thenComparing(edge -> edge));

        Map<String, String> parent = new HashMap<>();
        for (String node : graph.nodes()) {
            parent.put(node, node);
        }

        List<GraphEdge> forest = new ArrayList<>();

        for (GraphEdge candidate : candidates) {
            String rootFrom = find(parent, candidate.from());
            String rootTo = find(parent, candidate.to());

            if (rootFrom.equals(rootTo)) {
                continue;
            }

            parent.put(rootFrom, rootTo);
            forest.add(candidate);
        }

        forest.sort(Comparator.naturalOrder());

        return forest;
    }

    private static String find(Map<String, String> parent, String node) {
        String current = node;
        Collection<String> path = new ArrayList<>();

        while (!parent.get(current).equals(current)) {
            path.add(current);
            current = parent.get(current);
        }

        for (String step : path) {
            parent.put(step, current);
        }

        return current;
    }
}
