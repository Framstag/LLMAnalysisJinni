package com.framstag.llmaj.tools.java.language;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How far the vocabulary of a module agrees with what the module says it is for.
 *
 * <p>The purpose and the architecture description of a module are the module's own claim about its domain.
 * A description word that the code never uses is a concept that was stated and not implemented, and a
 * frequent name that the description never mentions is a concept that lives in the code without being
 * declared. Neither is a defect on its own, which is why the analysis reports the two lists and not a
 * verdict.
 *
 * <p>Only the counted tiers take part, on both sides: the stop words of an English sentence say nothing
 * about a concept, and counting them would deflate every coverage value by the same amount.
 *
 * <p>Absence is reported as absence. A module without a description reports no coverage value at all
 * instead of a zero, because a missing description is not a missing coverage.
 */
public record TextAlignment(Double purposeCoverage,
                            Double codeCoverage,
                            Double documentationOverlap,
                            List<String> absentPurposeTerms,
                            List<String> absentCodeTerms) {

    /**
     * The number of leading code terms whose presence in the description is measured. The leading terms
     * are the ones a reader would name as the concepts of the module.
     */
    public static final int LEADING_CODE_TERM_COUNT = 20;

    private static final double JACCARD_EMPTY = 0.0;

    /**
     * @return the alignment of a vocabulary with the description of its module, with the coverage values
     * and both absence lists unavailable when no description exists
     */
    public static TextAlignment of(Map<String, TermOccurrences> occurrencesByTerm,
                                   String description,
                                   DomainLexicon lexicon,
                                   int termLimit) {
        Double documentationOverlap = documentationOverlapOf(occurrencesByTerm, lexicon);

        Set<String> descriptionTokens = countedTokens(ProseText.tokens(description), lexicon);

        if (descriptionTokens.isEmpty()) {
            return new TextAlignment(null, null, documentationOverlap, List.of(), List.of());
        }

        Map<String, LexiconTier> codeTerms = countedTerms(occurrencesByTerm, lexicon);
        Set<String> absentPurpose = new LinkedHashSet<>(descriptionTokens);
        absentPurpose.removeAll(codeTerms.keySet());

        List<String> leadingCodeTerms = VocabularyShares.leadingDomainTerms(occurrencesByTerm, lexicon,
                LEADING_CODE_TERM_COUNT);

        List<String> absentCode = leadingCodeTerms.stream()
                .filter(term -> !descriptionTokens.contains(term))
                .limit(termLimit)
                .toList();

        int describedLeadingTerms = (int) leadingCodeTerms.stream()
                .filter(descriptionTokens::contains)
                .count();
        int consideredLeadingTerms = Math.min(LEADING_CODE_TERM_COUNT, leadingCodeTerms.size());

        return new TextAlignment(share(descriptionTokens.size() - absentPurpose.size(), descriptionTokens.size()),
                share(describedLeadingTerms, consideredLeadingTerms),
                documentationOverlap,
                orderedByTierThenTerm(absentPurpose, lexicon, termLimit),
                absentCode);
    }

    private static Double documentationOverlapOf(Map<String, TermOccurrences> occurrencesByTerm,
                                                 DomainLexicon lexicon) {
        Set<String> identifierTokens = new LinkedHashSet<>();
        Set<String> documentationTokens = new LinkedHashSet<>();

        for (Map.Entry<String, TermOccurrences> entry : occurrencesByTerm.entrySet()) {
            if (!lexicon.tierOf(entry.getKey()).countsInShares()) {
                continue;
            }

            if (entry.getValue().getOccurrences(VocabularyChannel.DOC) > 0) {
                documentationTokens.add(entry.getKey());
            }

            if (hasIdentifierChannel(entry.getValue())) {
                identifierTokens.add(entry.getKey());
            }
        }

        if (identifierTokens.isEmpty() || documentationTokens.isEmpty()) {
            return null;
        }

        int shared = 0;

        for (String token : identifierTokens) {
            if (documentationTokens.contains(token)) {
                shared++;
            }
        }

        int union = identifierTokens.size() + documentationTokens.size() - shared;

        return union == 0 ? JACCARD_EMPTY : (double) shared / union;
    }

    private static boolean hasIdentifierChannel(TermOccurrences occurrences) {
        return occurrences.getOccurrences(VocabularyChannel.TYPE) > 0
                || occurrences.getOccurrences(VocabularyChannel.METHOD) > 0
                || occurrences.getOccurrences(VocabularyChannel.METHOD_VERB) > 0
                || occurrences.getOccurrences(VocabularyChannel.FIELD) > 0
                || occurrences.getOccurrences(VocabularyChannel.PACKAGE) > 0;
    }

    private static Map<String, LexiconTier> countedTerms(Map<String, TermOccurrences> occurrencesByTerm,
                                                        DomainLexicon lexicon) {
        Map<String, LexiconTier> terms = new LinkedHashMap<>();

        for (String term : occurrencesByTerm.keySet()) {
            LexiconTier tier = lexicon.tierOf(term);

            if (tier.countsInShares()) {
                terms.put(term, tier);
            }
        }

        return terms;
    }

    private static Set<String> countedTokens(List<String> tokens, DomainLexicon lexicon) {
        Set<String> result = new LinkedHashSet<>();

        for (String token : tokens) {
            if (lexicon.tierOf(token).countsInShares()) {
                result.add(token);
            }
        }

        return result;
    }

    /**
     * A reader looks for the domain words first, so a candidate term leads the list and the plumbing
     * follows it.
     */
    private static List<String> orderedByTierThenTerm(Set<String> absentTerms, DomainLexicon lexicon, int limit) {
        List<String> ordered = new ArrayList<>(absentTerms);

        ordered.sort(Comparator
                .comparing((String term) -> !lexicon.tierOf(term).isDomainCandidate())
                .thenComparing(Comparator.naturalOrder()));

        return ordered.stream().limit(limit).toList();
    }

    private static double share(int part, int total) {
        return total == 0 ? 0.0 : (double) part / total;
    }
}
