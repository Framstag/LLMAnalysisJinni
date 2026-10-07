package com.framstag.llmaj.tools.java.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The connected components of a graph, optionally after removing a set of edges.
 *
 * <p>Removing edges rather than keeping the heavy ones is what makes this the thresholding step of a
 * single linkage separation: removing every edge up to a weight is the same as keeping only the edges above
 * that weight.
 */
public final class ConnectedComponents {
    private ConnectedComponents() {
    }

    public static List<Set<String>> of(WeightedGraph graph) {
        return of(graph, List.of());
    }

    public static List<Set<String>> of(WeightedGraph graph, Collection<GraphEdge> removedEdges) {
        Set<GraphEdge> removed = new HashSet<>(removedEdges);
        Set<String> visited = new HashSet<>();
        List<Set<String>> components = new ArrayList<>();

        for (String start : new TreeSet<>(graph.nodes())) {
            if (visited.contains(start)) {
                continue;
            }

            Set<String> component = new TreeSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.push(start);
            visited.add(start);

            while (!pending.isEmpty()) {
                String current = pending.pop();
                component.add(current);

                for (String neighbour : graph.neighbours(current)) {
                    if (visited.contains(neighbour)) {
                        continue;
                    }

                    if (removed.contains(new GraphEdge(current, neighbour, graph.weight(current, neighbour)))) {
                        continue;
                    }

                    visited.add(neighbour);
                    pending.push(neighbour);
                }
            }

            components.add(component);
        }

        return components;
    }
}
