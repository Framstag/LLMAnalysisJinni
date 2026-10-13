package com.framstag.llmaj.tools.java.language;

import com.framstag.llmaj.config.Config;

import java.nio.file.Path;
import java.util.Map;

/**
 * The values the vocabulary analysis is steered by. They are part of the workspace configuration, so a
 * reader of {@code config.json} can see what a report was produced with, and the reference document names
 * them with the value a workspace is seeded with.
 *
 * @param lexiconPath            the file that classifies the terms
 * @param minimumCandidateTerms  the lowest number of candidate domain terms a module needs before its
 *                               metrics are reported at all
 * @param topTermsPerModule      how many leading terms per module the compact result carries to the model
 */
public record DomainLanguageSettings(Path lexiconPath, int minimumCandidateTerms, int topTermsPerModule) {

    public static final String DEFAULT_LEXICON_PATH = "analysis/software-architecture/facts/domain_lexicon.csv";

    /**
     * A handful of identifiers do not describe a domain, and a share computed from them is noise with a decimal
     * point. The bound is deliberately low: a real project has many small modules, and a gate that keeps most
     * of them out of the judgement answers nothing. A module below it reports no share, but it does report the
     * candidate terms it has, so a reader sees why the module was not judged.
     */
    public static final int DEFAULT_MINIMUM_CANDIDATE_TERMS = 50;

    /**
     * The full term list stays in the CSV; this bound only keeps the result a model reads small.
     */
    public static final int DEFAULT_TOP_TERMS = 20;

    /**
     * @return the settings of the run, with the lexicon path resolved against the directory the analysis runs
     * in, which is the directory the configured analysis directory is relative to as well
     */
    public static DomainLanguageSettings from(Map<String, String> properties) {
        return from(properties, Path.of("").toAbsolutePath());
    }

    /**
     * @param runDirectory the directory an absolute lexicon path is taken from, and the directory a relative
     *                     one is resolved against
     */
    public static DomainLanguageSettings from(Map<String, String> properties, Path runDirectory) {
        String configuredPath = properties == null ? null : properties.get(Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY);
        String lexicon = (configuredPath == null || configuredPath.isBlank())
                ? DEFAULT_LEXICON_PATH
                : configuredPath.trim();

        return new DomainLanguageSettings(lexiconPath(lexicon, runDirectory),
                budget(properties, Config.DOMAIN_LANGUAGE_MINIMUM_CANDIDATE_TERMS_PROPERTY,
                        DEFAULT_MINIMUM_CANDIDATE_TERMS),
                budget(properties, Config.DOMAIN_LANGUAGE_TOP_TERMS_PROPERTY, DEFAULT_TOP_TERMS));
    }

    private static Path lexiconPath(String lexicon, Path runDirectory) {
        Path path = Path.of(lexicon);

        if (path.isAbsolute()) {
            return path;
        }

        return runDirectory.resolve(path);
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
