package com.framstag.llmaj.tools.java.language.overview;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The project-level language picture: the vocabulary of every module, how much of it the modules share, which
 * module's language leaked into which other one, and how the sharing lines up with the imports.
 *
 * <p>This is the whole of what the project-level analysis knows, and everything the context map judgement
 * reads. It is computed once, written to disk as CSV evidence and handed to the model in a bounded form.
 */
public record DomainLanguageOverview(ProjectVocabulary vocabulary,
                                     LexicalOverlap overlap,
                                     List<TermIntrusion> intrusions,
                                     List<QuadrantRow> quadrant,
                                     List<ProjectVocabulary.TermSpan> ubiquitousTerms,
                                     ImportMatrix importMatrix) {

    /**
     * @return the number of module pairs per classification, for the model and for the report
     */
    public Map<String, Integer> classificationCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (String classification : QuadrantRow.classifications()) {
            counts.put(classification, 0);
        }

        for (QuadrantRow row : quadrant) {
            counts.merge(row.classification(), 1, Integer::sum);
        }

        return counts;
    }

    /**
     * @return the pairs of one classification, the pair with the larger shared language first, bounded by the
     * configured count
     */
    public List<QuadrantRow> pairsOf(String classification, int limit) {
        return quadrant.stream()
                .filter(row -> row.classification().equals(classification))
                .sorted(Comparator
                        .comparingInt((QuadrantRow row) -> row.sharedLeadingTerms().size()).reversed()
                        .thenComparing(QuadrantRow::firstModule)
                        .thenComparing(QuadrantRow::secondModule))
                .limit(limit)
                .toList();
    }
}
