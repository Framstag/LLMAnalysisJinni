package com.framstag.llmaj.tools.java.language.overview;

import com.framstag.llmaj.config.Config;

import java.util.Map;

/**
 * The bounds of the project-level language overview, read from the workspace configuration.
 *
 * @param leadingTerms               how many leading candidate terms of a module establish its domain and are
 *                                   carried into the compact result; the CSV evidence keeps every term
 * @param maxPairsPerClassification  how many module pairs of one classification the compact result carries
 * @param ubiquitousModuleShare      the share of the modules above which a term is the vocabulary of the whole
 *                                   project rather than the language of a boundary
 */
public record ContextMapSettings(int leadingTerms,
                                 int maxPairsPerClassification,
                                 double ubiquitousModuleShare) {

    /**
     * The leading terms of a module that establish its domain. Twenty is the same bound the per-module
     * evidence uses for its own leading terms, so ownership and the per-module view agree.
     */
    public static final int DEFAULT_LEADING_TERMS = 20;

    /**
     * The pair list of a large project is quadratic in its modules, and the tool result has to stay small
     * enough to be read, so the result carries the most significant pairs of each classification while the
     * CSV keeps every pair.
     */
    public static final int DEFAULT_MAX_PAIRS_PER_CLASSIFICATION = 25;

    /**
     * A term more than half of the modules use is the project's own vocabulary: its name, the root of its
     * packages, or a word every layer repeats. Such a term marks no boundary, because there is no pair of
     * modules it does not connect, so the lexical axis of the quadrant leaves it out and the evidence reports
     * it as a view of its own.
     */
    public static final double DEFAULT_UBIQUITOUS_MODULE_SHARE = 0.5;

    public static ContextMapSettings from(Map<String, String> properties) {
        return new ContextMapSettings(budget(properties, Config.CONTEXT_MAP_LEADING_TERMS_PROPERTY,
                        DEFAULT_LEADING_TERMS),
                budget(properties, Config.CONTEXT_MAP_MAX_PAIRS_PROPERTY, DEFAULT_MAX_PAIRS_PER_CLASSIFICATION),
                share(properties, Config.CONTEXT_MAP_UBIQUITOUS_SHARE_PROPERTY, DEFAULT_UBIQUITOUS_MODULE_SHARE));
    }

    /**
     * @return the minimum number of modules a candidate term has to be used by to count as project-wide, or
     * {@code Integer.MAX_VALUE} when nothing can be project-wide in this project
     */
    public int ubiquitousModuleCount(int modules) {
        if (modules < MINIMUM_MODULES) {
            return Integer.MAX_VALUE;
        }

        return (int) Math.floor(ubiquitousModuleShare * modules) + 1;
    }

    /**
     * Below this many modules, a project has no vocabulary of its own to speak of: with two modules a word both
     * of them use is the language of the one boundary there is.
     */
    private static final int MINIMUM_MODULES = 3;

    private static double share(Map<String, String> properties, String propertyName, double defaultValue) {
        if (properties == null) {
            return defaultValue;
        }

        String value = properties.get(propertyName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            double parsed = Double.parseDouble(value.trim());

            return (parsed > 0.0 && parsed <= 1.0) ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int budget(Map<String, String> properties, String propertyName, int defaultValue) {
        if (properties == null) {
            return defaultValue;
        }

        String value = properties.get(propertyName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(value.trim());

            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
