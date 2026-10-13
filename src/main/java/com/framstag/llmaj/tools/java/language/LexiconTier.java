package com.framstag.llmaj.tools.java.language;

/**
 * The tier a vocabulary token is classified into.
 *
 * <p>The three named tiers say what a token is not: plumbing, generic shape or language boilerplate. What
 * is left over, {@link #UNCLASSIFIED}, is what the analysis calls candidate domain vocabulary, because no
 * lexicon can know the domain of a project it has never seen.
 */
public enum LexiconTier {
    TECHNICAL("technical"),
    GENERIC("generic"),
    NOISE("noise"),
    UNCLASSIFIED("unclassified");

    private final String value;

    LexiconTier(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * @return true when the tier counts as candidate domain vocabulary and therefore takes part in the
     * shares of the vocabulary metrics
     */
    public boolean isDomainCandidate() {
        return this == UNCLASSIFIED;
    }

    /**
     * @return true when the tier counts in the denominator of the vocabulary shares
     */
    public boolean countsInShares() {
        return this != NOISE;
    }

    public static LexiconTier fromString(String text) {
        for (LexiconTier tier : LexiconTier.values()) {
            if (tier.value.equalsIgnoreCase(text.trim())) {
                return tier;
            }
        }

        throw new IllegalArgumentException("Unknown lexicon tier '" + text + "'");
    }
}
