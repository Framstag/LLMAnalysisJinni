package com.framstag.llmaj.tools.java.language;

import com.framstag.llmaj.tools.java.Module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the analysis knows about the language of one module, computed from the module report in one
 * pass: the counted terms with their channels and tiers, the shares, the alignment with the description and
 * the naming cohesion.
 *
 * <p>A module whose candidate vocabulary is below the configured minimum carries the insufficient evidence
 * state and no metric values at all. Twenty identifiers do not describe a domain, and a share computed from
 * them would be noise with a decimal point.
 */
public record ModuleLanguageEvidence(String moduleName,
                                     String lexiconVersion,
                                     boolean sufficientEvidence,
                                     int candidateTerms,
                                     int minimumCandidateTerms,
                                     VocabularyShares shares,
                                     Map<String, Integer> namedTierOccurrences,
                                     TextAlignment alignment,
                                     NamingCohesion cohesion,
                                     List<TermEvidence> terms) {

    /**
     * One counted term, with the channels it occurred in. The tier is carried along so that a reader of the
     * evidence sees the classification that produced the shares, and so that a wrong entry of the lexicon
     * stays visible instead of silently disappearing from the count.
     */
    public record TermEvidence(String term, LexiconTier tier, int occurrences,
                               Map<VocabularyChannel, Integer> occurrencesByChannel) {

        public int getOccurrences(VocabularyChannel channel) {
            return occurrencesByChannel.getOrDefault(channel, 0);
        }
    }

    public static ModuleLanguageEvidence of(String moduleName,
                                            Module module,
                                            String description,
                                            DomainLexicon lexicon,
                                            int minimumCandidateTerms,
                                            int termLimit) {
        ModuleVocabulary vocabulary = ModuleVocabulary.of(module);
        Map<String, TermOccurrences> occurrencesByTerm = vocabulary.getOccurrencesByTerm();
        VocabularyShares shares = VocabularyShares.of(occurrencesByTerm, lexicon);
        boolean sufficientEvidence = shares.distinctDomainTerms() >= minimumCandidateTerms;

        TextAlignment alignment = sufficientEvidence
                ? TextAlignment.of(occurrencesByTerm, description, lexicon, termLimit)
                : new TextAlignment(null, null, null, List.of(), List.of());

        NamingCohesion cohesion = sufficientEvidence
                ? NamingCohesion.of(module)
                : new NamingCohesion(null, null, null, List.of());

        return new ModuleLanguageEvidence(moduleName,
                lexicon.getVersion(),
                sufficientEvidence,
                shares.distinctDomainTerms(),
                minimumCandidateTerms,
                shares,
                VocabularyShares.namedTierOccurrences(occurrencesByTerm, lexicon),
                alignment,
                cohesion,
                terms(occurrencesByTerm, lexicon));
    }

    /**
     * @return every distinct term of the module, sorted by term, including the terms the metrics drop, so
     * that the evidence is complete and a wrong tier can be found and corrected
     */
    private static List<TermEvidence> terms(Map<String, TermOccurrences> occurrencesByTerm,
                                           DomainLexicon lexicon) {
        List<String> sortedTerms = new ArrayList<>(occurrencesByTerm.keySet());
        sortedTerms.sort(String::compareTo);

        List<TermEvidence> terms = new ArrayList<>(sortedTerms.size());

        for (String term : sortedTerms) {
            TermOccurrences occurrences = occurrencesByTerm.get(term);
            Map<VocabularyChannel, Integer> occurrencesByChannel = new LinkedHashMap<>();

            for (VocabularyChannel channel : VocabularyChannel.values()) {
                int count = occurrences.getOccurrences(channel);

                if (count > 0) {
                    occurrencesByChannel.put(channel, count);
                }
            }

            terms.add(new TermEvidence(term, lexicon.tierOf(term), occurrences.getOccurrences(),
                    occurrencesByChannel));
        }

        return List.copyOf(terms);
    }

    /**
     * @return the leading candidate terms, most frequent first, for the compact rollup
     */
    public List<String> leadingTerms(int limit) {
        return terms.stream()
                .filter(term -> term.tier().isDomainCandidate())
                .sorted((first, second) -> {
                    int byOccurrences = Integer.compare(second.occurrences(), first.occurrences());

                    return byOccurrences != 0 ? byOccurrences : first.term().compareTo(second.term());
                })
                .limit(limit)
                .map(TermEvidence::term)
                .toList();
    }
}
