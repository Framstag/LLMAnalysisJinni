package com.framstag.llmaj.tools.java.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The exact global minimum cut of an undirected weighted graph, by Stoer-Wagner.
 *
 * <p>This is the secondary figure of a separation: the least total coupling that has to be given up to break
 * the graph in two. It costs a cubic pass, so it is only computed for a module whose class count is within
 * the configured limit. It is deterministic and needs no parameter.
 */
public final class MinimumCut {
    /**
     * The class count above which the exact minimum cut is skipped. The computation is cubic, so this is the
     * bound that keeps a large module from stalling the run; the separation ladder does not depend on it.
     */
    public static final int DEFAULT_NODE_LIMIT = 400;

    private MinimumCut() {
    }

    /**
     * @param weight the total weight of the edges crossing the cut
     * @param side   one side of the cut; the other side is every other node
     */
    public record Cut(int weight, Set<String> side) {
        public Cut {
            side = Set.copyOf(side);
        }
    }

    public static Optional<Cut> of(WeightedGraph graph) {
        List<String> remainingNodes = new ArrayList<>(new TreeSet<>(graph.nodes()));

        if (remainingNodes.size() < 2) {
            return Optional.empty();
        }

        Map<String, Map<String, Integer>> weights = new HashMap<>();
        Map<String, Set<String>> mergedNodes = new HashMap<>();

        for (String node : remainingNodes) {
            Map<String, Integer> neighbours = new HashMap<>();

            for (String neighbour : graph.neighbours(node)) {
                neighbours.put(neighbour, graph.weight(node, neighbour));
            }

            weights.put(node, neighbours);
            mergedNodes.put(node, new TreeSet<>(Set.of(node)));
        }

        int bestWeight = Integer.MAX_VALUE;
        Set<String> bestSide = null;

        while (remainingNodes.size() > 1) {
            List<String> order = maximumAdjacencyOrder(remainingNodes, weights);

            String last = order.getLast();
            String secondToLast = order.get(order.size() - 2);
            int phaseWeight = connectionToPrevious(order, weights, last);

            if (phaseWeight < bestWeight) {
                bestWeight = phaseWeight;
                bestSide = new TreeSet<>(mergedNodes.get(last));
            }

            merge(weights, secondToLast, last, remainingNodes);
            mergedNodes.get(secondToLast).addAll(mergedNodes.get(last));
            mergedNodes.remove(last);
            remainingNodes.remove(last);
            weights.remove(last);

            for (String node : remainingNodes) {
                weights.get(node).remove(last);
            }
        }

        return Optional.of(new Cut(bestWeight, bestSide));
    }

    /**
     * The maximum adjacency search: repeatedly add the node with the heaviest connection to the nodes already
     * in the order. The last two nodes of the order identify a cut that is a candidate for the minimum.
     */
    private static List<String> maximumAdjacencyOrder(List<String> nodes,
                                                      Map<String, Map<String, Integer>> weights) {
        List<String> order = new ArrayList<>();
        Collection<String> pending = new LinkedHashSet<>(nodes);
        Map<String, Integer> connection = new HashMap<>();

        while (!pending.isEmpty()) {
            String heaviest = null;
            int heaviestConnection = -1;

            for (String candidate : pending) {
                int candidateConnection = connection.getOrDefault(candidate, 0);

                if (candidateConnection > heaviestConnection
                        || (candidateConnection == heaviestConnection
                        && heaviest != null && candidate.compareTo(heaviest) < 0)) {
                    heaviest = candidate;
                    heaviestConnection = candidateConnection;
                }
            }

            order.add(heaviest);
            pending.remove(heaviest);

            for (String neighbour : pending) {
                connection.merge(neighbour, weights.get(heaviest).getOrDefault(neighbour, 0), Integer::sum);
            }
        }

        return order;
    }

    private static int connectionToPrevious(List<String> order,
                                            Map<String, Map<String, Integer>> weights,
                                            String last) {
        int connection = 0;

        for (int i = 0; i < order.size() - 1; i++) {
            connection += weights.get(order.get(i)).getOrDefault(last, 0);
        }

        return connection;
    }

    private static void merge(Map<String, Map<String, Integer>> weights,
                              String keep,
                              String drop,
                              List<String> remainingNodes) {
        for (String node : remainingNodes) {
            if (node.equals(keep) || node.equals(drop)) {
                continue;
            }

            int merged = weights.get(keep).getOrDefault(node, 0) + weights.get(drop).getOrDefault(node, 0);

            if (merged > 0) {
                weights.get(keep).put(node, merged);
                weights.get(node).put(keep, merged);
            }
        }
    }
}
