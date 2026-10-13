package com.framstag.llmaj.tools.java.language.overview;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The pair of two modules on both axes at once: how much they import from each other and how much language
 * they share.
 *
 * <p>The classification answers the question a single axis cannot answer. Two modules that share a domain
 * vocabulary and no import are a candidate domain leak, because nothing in the code connects them and yet
 * they talk about the same thing. Two modules that import each other and share no vocabulary are a
 * translation: the boundary pays for itself by translating. Two coupled modules that share vocabulary are
 * coupled, and two independent ones that share nothing are independent.
 *
 * <p>The classification is a candidate and never a finding of fact. Both numbers are reported beside it, so a
 * reader can apply their own bound instead of the one the analysis applied.
 */
public record QuadrantRow(String firstModule,
                          String secondModule,
                          Integer importCount,
                          double overlap,
                          List<String> sharedLeadingTerms,
                          String classification) {

    public static final String CANDIDATE_DOMAIN_LEAK = "candidate domain leak";

    public static final String TRANSLATION = "translation";

    public static final String COUPLED = "coupled";

    public static final String INDEPENDENT = "independent";

    /**
     * The structural side of the quadrant is unknown when the inter-module dependency analysis has not run. The
     * row then says so instead of claiming an import count of zero, which would turn every pair into a leak.
     */
    public static final String STRUCTURAL_EVIDENCE_MISSING = "structural evidence missing";

    public static List<QuadrantRow> of(ProjectVocabulary vocabulary,
                                      LexicalOverlap overlap,
                                      ImportMatrix matrix,
                                      int leadingTermLimit,
                                      Set<String> ubiquitousTerms) {
        List<QuadrantRow> rows = new ArrayList<>(overlap.pairs().size());

        for (LexicalOverlap.PairOverlap pair : overlap.pairs()) {
            List<String> sharedLeadingTerms = LexicalOverlap.sharedLeadingTerms(vocabulary,
                    pair.firstModule(),
                    pair.secondModule(),
                    leadingTermLimit,
                    ubiquitousTerms);

            Integer importCount = matrix.importCountBetween(pair.firstModule(), pair.secondModule());

            rows.add(new QuadrantRow(pair.firstModule(),
                    pair.secondModule(),
                    importCount,
                    pair.overlap(),
                    sharedLeadingTerms,
                    classify(importCount, sharedLeadingTerms)));
        }

        return List.copyOf(rows);
    }

    private static String classify(Integer importCount, List<String> sharedLeadingTerms) {
        if (importCount == null) {
            return STRUCTURAL_EVIDENCE_MISSING;
        }

        boolean lexical = !sharedLeadingTerms.isEmpty();
        boolean structural = importCount > 0;

        if (structural) {
            return lexical ? COUPLED : TRANSLATION;
        }

        return lexical ? CANDIDATE_DOMAIN_LEAK : INDEPENDENT;
    }

    public static List<String> classifications() {
        return List.of(CANDIDATE_DOMAIN_LEAK, TRANSLATION, COUPLED, INDEPENDENT, STRUCTURAL_EVIDENCE_MISSING);
    }
}
