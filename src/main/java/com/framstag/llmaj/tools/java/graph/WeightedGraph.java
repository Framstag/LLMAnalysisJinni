package com.framstag.llmaj.tools.java.graph;

import java.util.Collection;
import java.util.Set;

/**
 * The undirected graph of measured class couplings.
 *
 * <p>The algorithms below are written against this interface rather than against a concrete graph, so a graph
 * library can replace the implementation without touching a single caller.
 */
public interface WeightedGraph {
    /**
     * @return every node, including a node that has no edge
     */
    Collection<String> nodes();

    /**
     * @return the nodes adjacent to the given node, empty for an unknown or unconnected node
     */
    Set<String> neighbours(String node);

    /**
     * @return the weight of the edge between the two nodes, or zero when they are not adjacent
     */
    int weight(String from, String to);

    /**
     * @return every edge once, in either direction
     */
    Collection<GraphEdge> edges();
}
