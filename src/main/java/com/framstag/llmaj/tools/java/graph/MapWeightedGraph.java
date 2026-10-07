package com.framstag.llmaj.tools.java.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A weighted graph held in adjacency maps. Every view it hands out is sorted, so two runs over the same
 * input produce the same traversal order and the algorithms built on top stay deterministic.
 */
public final class MapWeightedGraph implements WeightedGraph {
    private final Map<String, Map<String, Integer>> adjacency = new TreeMap<>();

    /**
     * Adds a node without an edge. A node that never gets an edge is still a node of the graph, so it has to
     * be declared rather than left out.
     */
    public void addNode(String node) {
        adjacency.computeIfAbsent(node, ignored -> new TreeMap<>());
    }

    /**
     * Adds an undirected edge. A self reference is ignored, because a class referencing itself is not a
     * coupling between two classes, and adding the same pair twice keeps the heavier weight instead of
     * summing it, so the call is idempotent.
     */
    public void addEdge(String from, String to, int weight) {
        if (from == null || to == null || from.equals(to) || weight <= 0) {
            return;
        }

        addNode(from);
        addNode(to);

        adjacency.get(from).merge(to, weight, Math::max);
        adjacency.get(to).merge(from, weight, Math::max);
    }

    @Override
    public Collection<String> nodes() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    @Override
    public Set<String> neighbours(String node) {
        Map<String, Integer> neighbours = adjacency.get(node);

        return neighbours == null ? Set.of() : Collections.unmodifiableSet(neighbours.keySet());
    }

    @Override
    public int weight(String from, String to) {
        Map<String, Integer> neighbours = adjacency.get(from);

        return neighbours == null ? 0 : neighbours.getOrDefault(to, 0);
    }

    @Override
    public Collection<GraphEdge> edges() {
        List<GraphEdge> edges = new ArrayList<>();

        for (Map.Entry<String, Map<String, Integer>> entry : adjacency.entrySet()) {
            for (Map.Entry<String, Integer> neighbour : entry.getValue().entrySet()) {
                if (entry.getKey().compareTo(neighbour.getKey()) < 0) {
                    edges.add(new GraphEdge(entry.getKey(), neighbour.getKey(), neighbour.getValue()));
                }
            }
        }

        Collections.sort(edges);

        return Collections.unmodifiableList(edges);
    }
}
