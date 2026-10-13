package com.framstag.llmaj.tools.java.language.overview;

import com.framstag.llmaj.tools.java.language.ProseText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Terms that one module establishes and another one uses.
 *
 * <p>A module establishes a term when the term is one of the leading candidate terms of its own domain
 * vocabulary. Ownership is a set and not a name: a term that three modules establish is owned by those three,
 * which is what makes the overview able to say "this word belongs to that boundary, and this other module
 * borrows it".
 *
 * <p>A term a module owns itself is never an intrusion, whatever its share, and a term whose owner has no
 * description is reported with the ownership unknown rather than as absent from the description: not writing
 * a description is not the same as not using a word.
 */
public record TermIntrusion(String module,
                            String term,
                            List<String> ownerModules,
                            int occurrences,
                            double domainShare,
                            Boolean ownersCarryTerm) {

    /**
     * @param vocabulary     the counted vocabulary of the project
     * @param descriptions   the description of each module, or no entry for a module that was never described
     * @param leadingTermLimit how many leading candidate terms of a module establish its domain
     * @return one entry per term a module uses and does not own, most of the module's vocabulary first
     */
    public static List<TermIntrusion> of(ProjectVocabulary vocabulary,
                                        Map<String, String> descriptions,
                                        int leadingTermLimit,
                                        Set<String> ubiquitousTerms) {
        Map<String, Set<String>> ownersByTerm = ownersOfEstablishedTerms(vocabulary, leadingTermLimit,
                ubiquitousTerms);
        List<TermIntrusion> intrusions = new ArrayList<>();

        for (String module : vocabulary.modules()) {
            List<ProjectVocabulary.TermRow> candidateTerms = vocabulary.candidateTermsOfModule(module,
                    ubiquitousTerms);
            int domainOccurrences = candidateTerms.stream().mapToInt(ProjectVocabulary.TermRow::occurrences).sum();

            for (ProjectVocabulary.TermRow row : candidateTerms) {
                List<String> owners = ownerModulesOf(row.term(), module, ownersByTerm);

                if (owners.isEmpty()) {
                    continue;
                }

                intrusions.add(new TermIntrusion(module,
                        row.term(),
                        owners,
                        row.occurrences(),
                        domainOccurrences == 0 ? 0.0 : (double) row.occurrences() / domainOccurrences,
                        ownersCarryTerm(owners, row.term(), descriptions)));
            }
        }

        intrusions.sort(Comparator.comparingDouble(TermIntrusion::domainShare).reversed()
                .thenComparing(TermIntrusion::term)
                .thenComparing(TermIntrusion::module));

        return List.copyOf(intrusions);
    }

    /**
     * @return the modules that establish each term: a term is established by a module when it is one of the
     * leading candidate terms of that module
     */
    private static Map<String, Set<String>> ownersOfEstablishedTerms(ProjectVocabulary vocabulary,
                                                                     int leadingTermLimit,
                                                                     Set<String> ubiquitousTerms) {
        Map<String, Set<String>> ownersByTerm = new LinkedHashMap<>();

        for (String module : vocabulary.modules()) {
            int established = 0;

            for (ProjectVocabulary.TermRow row : vocabulary.candidateTermsOfModule(module, ubiquitousTerms)) {
                if (established >= leadingTermLimit) {
                    break;
                }

                ownersByTerm.computeIfAbsent(row.term(), term -> new LinkedHashSet<>()).add(module);
                established++;
            }
        }

        return ownersByTerm;
    }

    /**
     * @return the modules that establish the term and are not the module itself. A module that establishes a
     * term of its own does not borrow it, whatever else the project does with the word; a module whose
     * vocabulary merely contains the term without establishing it is borrowing it from whoever does
     */
    private static List<String> ownerModulesOf(String term,
                                               String module,
                                               Map<String, Set<String>> ownersByTerm) {
        Set<String> owners = ownersByTerm.get(term);

        if (owners == null || owners.contains(module)) {
            return List.of();
        }

        return owners.stream().filter(owner -> !owner.equals(module)).sorted().toList();
    }

    /**
     * @return true when the description of an owner mentions the term, false when it does not, and null when
     * no owner has a description at all
     */
    private static Boolean ownersCarryTerm(List<String> owners, String term, Map<String, String> descriptions) {
        boolean described = false;

        for (String owner : owners) {
            String description = descriptions == null ? null : descriptions.get(owner);

            if (description == null) {
                continue;
            }

            described = true;

            if (mentions(description, term)) {
                return true;
            }
        }

        return described ? Boolean.FALSE : null;
    }

    /**
     * The description carries the term when the term stands as a word in it. The text is read the way the
     * vocabulary analysis reads a text, so a plural or a compound that merely contains the term does not count
     * as naming it.
     */
    private static boolean mentions(String description, String term) {
        return ProseText.tokens(description).contains(term.toLowerCase());
    }
}
