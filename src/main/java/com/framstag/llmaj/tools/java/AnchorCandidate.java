package com.framstag.llmaj.tools.java;

import java.util.List;

/**
 * One anchor candidate of the modernisation anchor analysis: the element a modernisation can attach to, the
 * module it is read in, the other modules whose reports hold the same element, the counted evidence it rests on
 * and the name of the analysis that counted that evidence.
 *
 * <p>A project whose aggregator module holds the same classes as its inner modules reports one element in several
 * module reports. The candidate is reported once, with {@link #module()} as the module it is read in and
 * {@link #alsoSeenIn()} as the other modules that hold it, so that the budget of a candidate list is not spent on
 * the same element several times.
 *
 * <p>The evidence is a short list of strings that carry the counted numbers, so that an anchor the analysis
 * forms from this candidate can name the number behind it. The source names the tool that counted the
 * evidence, so that a reader can find the report it was written to.
 */
public record AnchorCandidate(String element,
                              String module,
                              List<String> alsoSeenIn,
                              List<String> evidence,
                              String source) {

    /**
     * A candidate whose element is read in one module only.
     */
    public AnchorCandidate(String element, String module, List<String> evidence, String source) {
        this(element, module, List.of(), evidence, source);
    }
}
