package com.framstag.llmaj.tools.java.language;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shares of a module vocabulary: how much of it is plumbing, how much is generic shape, how much is a
 * candidate domain term, and how far a few terms carry the domain vocabulary.
 *
 * <p>Only the technical, generic and unclassified occurrences take part in the shares. The noise tier is
 * excluded from the denominator as well as from the result, because counting the boilerplate of the
 * language would make every module look equally technical.
 *
 * <p>No share carries a verdict. A module that writes an XML binding is expected to be almost entirely
 * technical, and one that models a domain is not. The judgement belongs to the analysis, not here.
 */
public record VocabularyShares(int distinctDomainTerms,
                               int technicalOccurrences,
                               int genericOccurrences,
                               int noiseOccurrences,
                               int domainOccurrences,
                               double technicalShare,
                               double genericShare,
                               double topTermShare) {

    /**
     * The number of leading candidate terms whose share is reported. A small number is deliberate: it
     * answers "does one word rule this module" without turning the report into a term ranking.
     */
    public static final int LEADING_TERM_COUNT = 5;

    public static VocabularyShares of(Map<String, TermOccurrences> occurrencesByTerm, DomainLexicon lexicon) {
        Map<LexiconTier, Integer> occurrencesByTier = tierOccurrences(occurrencesByTerm, lexicon);
        List<Map.Entry<String, TermOccurrences>> domainTerms = domainTerms(occurrencesByTerm, lexicon);

        int technicalOccurrences = occurrencesByTier.getOrDefault(LexiconTier.TECHNICAL, 0);
        int genericOccurrences = occurrencesByTier.getOrDefault(LexiconTier.GENERIC, 0);
        int noiseOccurrences = occurrencesByTier.getOrDefault(LexiconTier.NOISE, 0);
        int domainOccurrences = occurrencesByTier.getOrDefault(LexiconTier.UNCLASSIFIED, 0);

        int counted = technicalOccurrences + genericOccurrences + domainOccurrences;

        return new VocabularyShares(domainTerms.size(),
                technicalOccurrences,
                genericOccurrences,
                noiseOccurrences,
                domainOccurrences,
                share(technicalOccurrences, counted),
                share(genericOccurrences, counted),
                share(occurrencesOfLeadingTerms(domainTerms), domainOccurrences));
    }

    /**
     * @return the occurrence count per tier, including the tiers that did not occur, so a reader sees the
     * whole distribution of a module and not only the part that survived the lexicon
     */
    public static Map<LexiconTier, Integer> tierOccurrences(Map<String, TermOccurrences> occurrencesByTerm,
                                                            DomainLexicon lexicon) {
        Map<LexiconTier, Integer> result = new LinkedHashMap<>();

        for (LexiconTier tier : LexiconTier.values()) {
            result.put(tier, 0);
        }

        for (Map.Entry<String, TermOccurrences> entry : occurrencesByTerm.entrySet()) {
            result.merge(lexicon.tierOf(entry.getKey()), entry.getValue().getOccurrences(), Integer::sum);
        }

        return result;
    }

    /**
     * @return the candidate domain terms of the vocabulary, most frequent first, ties broken by the term
     */
    public static List<Map.Entry<String, TermOccurrences>> domainTerms(
            Map<String, TermOccurrences> occurrencesByTerm, DomainLexicon lexicon) {
        return occurrencesByTerm.entrySet().stream()
                .filter(entry -> lexicon.tierOf(entry.getKey()).isDomainCandidate())
                .sorted(byOccurrencesThenTerm())
                .toList();
    }

    /**
     * @return the leading candidate terms, for the compact rollup the model reads and for the evidence a
     * reader checks the shares against
     */
    public static List<String> leadingDomainTerms(Map<String, TermOccurrences> occurrencesByTerm,
                                                  DomainLexicon lexicon,
                                                  int limit) {
        return domainTerms(occurrencesByTerm, lexicon).stream()
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Comparator<Map.Entry<String, TermOccurrences>> byOccurrencesThenTerm() {
        return Comparator.comparingInt((Map.Entry<String, TermOccurrences> entry)
                        -> entry.getValue().getOccurrences()).reversed()
                .thenComparing(Map.Entry::getKey);
    }

    private static int occurrencesOfLeadingTerms(List<Map.Entry<String, TermOccurrences>> domainTerms) {
        return domainTerms.stream()
                .limit(LEADING_TERM_COUNT)
                .mapToInt(entry -> entry.getValue().getOccurrences())
                .sum();
    }

    private static double share(int part, int total) {
        return total == 0 ? 0.0 : (double) part / total;
    }

    /**
     * @return the occurrence counts per tier, keyed by the tier name the lexicon uses
     */
    public static Map<String, Integer> namedTierOccurrences(Map<String, TermOccurrences> occurrencesByTerm,
                                                            DomainLexicon lexicon) {
        Map<String, Integer> result = new LinkedHashMap<>();

        tierOccurrences(occurrencesByTerm, lexicon)
                .forEach((tier, occurrences) -> result.put(tier.getValue(), occurrences));

        return result;
    }
}
