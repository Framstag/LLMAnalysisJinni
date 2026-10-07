package com.framstag.llmaj.tools.java.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ranked ladder of candidate separations of a class reference graph, ordered by ascending cost.
 *
 * <p>A step exists for every distinct reference weight at which the partition of the graph actually changes.
 * The cost of a step is the weight of the tie that had to be severed, which is the strongest reference
 * between the two groups that came apart: as long as a single strong tie remains, the two sides are not
 * independent, however few ties there are.
 *
 * <p>Nothing here needs a resolution, a group count or a seed, so the ladder is the same on every run.
 */
public final class SeparationLadder {
    private SeparationLadder() {
    }

    /**
     * One step of the ladder.
     *
     * @param cost           the weight of the tie severed at this step, the strongest tie between the groups
     *                       that this step separates
     * @param severedEdges   every reference edge crossing between the resulting groups, ascending by weight
     * @param groups         the resulting groups, each sorted, and the groups ordered by their first member
     */
    public record Separation(int cost, List<GraphEdge> severedEdges, List<Set<String>> groups) {
        public Separation {
            severedEdges = List.copyOf(severedEdges);
            groups = List.copyOf(groups);
        }

        public int weakestSeveredWeight() {
            return severedEdges.isEmpty() ? 0 : severedEdges.getFirst().weight();
        }

        public int strongestSeveredWeight() {
            return severedEdges.isEmpty() ? 0 : severedEdges.getLast().weight();
        }

        public int totalSeveredWeight() {
            return severedEdges.stream().mapToInt(GraphEdge::weight).sum();
        }
    }

    public static List<Separation> of(WeightedGraph graph) {
        List<GraphEdge> forest = MaximumSpanningForest.of(graph);
        List<GraphEdge> allEdges = List.copyOf(graph.edges());
        List<Separation> ladder = new ArrayList<>();
        int previousGroupCount = ConnectedComponents.of(graph).size();

        for (Integer threshold : distinctWeights(forest)) {
            List<GraphEdge> removedEdges = allEdges.stream()
                    .filter(edge -> edge.weight() <= threshold)
                    .toList();

            List<Set<String>> groups = ConnectedComponents.of(graph, removedEdges);

            if (groups.size() <= previousGroupCount) {
                // Every edge at this weight lies inside a group that a heavier path already holds together,
                // so nothing came apart and the weight is not a candidate separation.
                continue;
            }

            ladder.add(new Separation(threshold, crossingEdges(allEdges, groups, threshold), groups));
            previousGroupCount = groups.size();
        }

        return Collections.unmodifiableList(ladder);
    }

    private static List<Integer> distinctWeights(List<GraphEdge> forest) {
        return forest.stream()
                .map(GraphEdge::weight)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The edges a separation severs, which are the edges between two different groups. The group of a node is
     * resolved through an index rather than by scanning the groups, because a module with a thousand classes
     * has a thousand groups at the end of its ladder.
     */
    private static List<GraphEdge> crossingEdges(List<GraphEdge> allEdges,
                                                List<Set<String>> groups,
                                                int threshold) {
        Map<String, Integer> groupOfNode = new HashMap<>();

        for (int index = 0; index < groups.size(); index++) {
            for (String member : groups.get(index)) {
                groupOfNode.put(member, index);
            }
        }

        List<GraphEdge> crossing = new ArrayList<>();

        for (GraphEdge edge : allEdges) {
            if (edge.weight() > threshold) {
                continue;
            }

            if (!groupOfNode.get(edge.from()).equals(groupOfNode.get(edge.to()))) {
                crossing.add(edge);
            }
        }

        return crossing;
    }
}
