package com.framstag.llmaj.tools.java.language.overview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How much language two modules have in common.
 *
 * <p>The measure is the overlap of the candidate domain terms of the two modules, which is the size of what
 * they share divided by the size of everything either of them names in its own domain. Only candidate terms
 * take part: two modules that share the word {@code result} share nothing.
 *
 * <p>Every pair that has vocabulary at all is reported, including the pair that shares no term. A pair with
 * an overlap of zero is the interesting one for the question whether two modules that share code also share
 * a language, so it must not be dropped as uninteresting.
 */
public record LexicalOverlap(List<PairOverlap> pairs) {

    /**
     * The shared and the exclusive candidate terms of one unordered pair of modules.
     */
    public record PairOverlap(String firstModule,
                              String secondModule,
                              List<String> sharedTerms,
                              double overlap,
                              List<String> exclusiveToFirst,
                              List<String> exclusiveToSecond) {
    }

    public static LexicalOverlap of(ProjectVocabulary vocabulary, java.util.Set<String> ubiquitousTerms) {
        List<String> modules = vocabulary.modules();
        List<PairOverlap> pairs = new ArrayList<>();

        for (int i = 0; i < modules.size(); i++) {
            for (int j = i + 1; j < modules.size(); j++) {
                String first = modules.get(i);
                String second = modules.get(j);
                Set<String> firstTerms = vocabulary.candidateTermSetOfModule(first, ubiquitousTerms);
                Set<String> secondTerms = vocabulary.candidateTermSetOfModule(second, ubiquitousTerms);

                Set<String> shared = new LinkedHashSet<>(firstTerms);
                shared.retainAll(secondTerms);

                Set<String> exclusiveToFirst = new LinkedHashSet<>(firstTerms);
                exclusiveToFirst.removeAll(secondTerms);

                Set<String> exclusiveToSecond = new LinkedHashSet<>(secondTerms);
                exclusiveToSecond.removeAll(firstTerms);

                pairs.add(new PairOverlap(first,
                        second,
                        shared.stream().sorted().toList(),
                        overlap(firstTerms, secondTerms),
                        exclusiveToFirst.stream().sorted().toList(),
                        exclusiveToSecond.stream().sorted().toList()));
            }
        }

        return new LexicalOverlap(List.copyOf(pairs));
    }

    /**
     * The pair of two modules, whichever order the caller knows them in.
     */
    public PairOverlap pairOf(String firstModule, String secondModule) {
        for (PairOverlap pair : pairs) {
            boolean matches = (pair.firstModule().equals(firstModule) && pair.secondModule().equals(secondModule))
                    || (pair.firstModule().equals(secondModule) && pair.secondModule().equals(firstModule));

            if (matches) {
                return pair;
            }
        }

        return null;
    }

    /**
     * @return the terms the two modules share that are a leading term of both, which is the vocabulary both of
     * them treat as theirs
     */
    public static List<String> sharedLeadingTerms(ProjectVocabulary vocabulary,
                                                 String firstModule,
                                                 String secondModule,
                                                 int leadingTermLimit,
                                                 Set<String> ubiquitousTerms) {
        Set<String> leadingOfFirst = leadingTermsOf(vocabulary, firstModule, leadingTermLimit, ubiquitousTerms);
        Set<String> leadingOfSecond = leadingTermsOf(vocabulary, secondModule, leadingTermLimit, ubiquitousTerms);

        leadingOfFirst.retainAll(leadingOfSecond);

        return leadingOfFirst.stream().sorted().toList();
    }

    private static Set<String> leadingTermsOf(ProjectVocabulary vocabulary,
                                             String module,
                                             int limit,
                                             Set<String> ubiquitousTerms) {
        Set<String> terms = new LinkedHashSet<>();

        for (ProjectVocabulary.TermRow row : vocabulary.candidateTermsOfModule(module, ubiquitousTerms)) {
            if (terms.size() >= limit) {
                break;
            }

            terms.add(row.term());
        }

        return terms;
    }

    private static double overlap(Set<String> firstTerms, Set<String> secondTerms) {
        if (firstTerms.isEmpty() && secondTerms.isEmpty()) {
            return 0.0;
        }

        int shared = 0;

        for (String term : firstTerms) {
            if (secondTerms.contains(term)) {
                shared++;
            }
        }

        return (double) shared / (firstTerms.size() + secondTerms.size() - shared);
    }
}
