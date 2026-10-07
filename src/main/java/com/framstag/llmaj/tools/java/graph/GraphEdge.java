package com.framstag.llmaj.tools.java.graph;

/**
 * An undirected, weighted edge of the class reference graph.
 *
 * <p>The endpoints are kept in a canonical order, so an edge has one identity regardless of the direction it
 * is looked at from. A weight is always positive: an edge exists because a measurable reference was counted,
 * and a structural relation that carries no reference site is not an edge of this graph at all.
 */
public record GraphEdge(String from, String to, int weight) implements Comparable<GraphEdge> {
    public GraphEdge {
        if (from == null || to == null) {
            throw new IllegalArgumentException("an edge needs two endpoints");
        }

        if (weight <= 0) {
            throw new IllegalArgumentException("an edge carries a positive weight, got " + weight);
        }

        if (from.compareTo(to) > 0) {
            String swap = from;
            from = to;
            to = swap;
        }
    }

    public boolean connects(String node) {
        return from.equals(node) || to.equals(node);
    }

    public String other(String node) {
        if (from.equals(node)) {
            return to;
        }

        if (to.equals(node)) {
            return from;
        }

        throw new IllegalArgumentException("edge " + this + " does not connect " + node);
    }

    @Override
    public int compareTo(GraphEdge other) {
        int byWeight = Integer.compare(weight, other.weight);

        if (byWeight != 0) {
            return byWeight;
        }

        int byFrom = from.compareTo(other.from);

        if (byFrom != 0) {
            return byFrom;
        }

        return to.compareTo(other.to);
    }
}
