package com.framstag.llmaj.tools.java.graph;

import java.util.List;

/**
 * Percentile ranking of a value inside a set of peer values.
 *
 * <p>Every factor of the god class ranking is expressed this way. A module-relative rank never claims that a
 * class is bad in absolute terms, only that it stands out against its peers, and it needs no threshold that
 * would have to be defended.
 */
public final class Percentiles {
    private Percentiles() {
    }

    /**
     * @return the share of peers the value exceeds, from 0 to 99. A value that ties with its peers receives
     * the same percentile as they do, and a set with a single element yields 0, because a class with no peer
     * to compare against does not stand out from anything.
     */
    public static int of(List<Double> values, double value) {
        if (values.isEmpty()) {
            return 0;
        }

        long below = values.stream().filter(peer -> peer < value).count();

        return (int) (below * 100 / values.size());
    }

    /**
     * The mirror of {@link #of}: the share of peers that exceed the value, from 0 to 99.
     *
     * <p>A factor where less is worse cannot be read by inverting {@link #of}. Cohesion is the case: in a
     * module where nearly every class is fully cohesive, the fully cohesive classes tie at the top, so they
     * exceed almost nobody and inverting the share of peers below them would hand every one of them a high
     * badness. Counting the peers above the value instead gives the best class 0 and the worst class the
     * share of peers it is behind, which is what the ranking needs.
     */
    public static int above(List<Double> values, double value) {
        if (values.isEmpty()) {
            return 0;
        }

        long above = values.stream().filter(peer -> peer > value).count();

        return (int) (above * 100 / values.size());
    }
}
