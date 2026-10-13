package com.framstag.llmaj.tools.java.language.overview;

import com.framstag.llmaj.tools.java.language.LexiconTier;
import com.framstag.llmaj.tools.java.language.ModuleLanguageEvidence;
import com.framstag.llmaj.tools.java.language.VocabularyChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The counted vocabulary of every module in one view: one row per module and term, and one rollup row per
 * term that names how many modules use it and which ones.
 *
 * <p>Terms are never merged across modules. The same word in two modules is two rows, because the question
 * the overview answers is where a word is used, not how often it occurs in the project.
 */
public record ProjectVocabulary(List<TermRow> rows, List<TermSpan> spans) {

    /**
     * One term as one module uses it.
     */
    public record TermRow(String module,
                          String term,
                          LexiconTier tier,
                          int occurrences,
                          Map<VocabularyChannel, Integer> occurrencesByChannel) {

        public int getOccurrences(VocabularyChannel channel) {
            return occurrencesByChannel.getOrDefault(channel, 0);
        }
    }

    /**
     * One term over the whole project: how often it occurs, how many modules use it and which ones, and the
     * tier most of its occurrences carry. The dominant tier answers "is this word plumbing" for a word that
     * one module counts as plumbing and another as a candidate domain term.
     */
    public record TermSpan(String term,
                           int occurrences,
                           List<String> modules,
                           LexiconTier dominantTier) {

        public int moduleCount() {
            return modules.size();
        }
    }

    public static ProjectVocabulary of(List<ModuleLanguageEvidence> evidence) {
        List<TermRow> rows = new ArrayList<>();

        for (ModuleLanguageEvidence moduleEvidence : evidence) {
            for (ModuleLanguageEvidence.TermEvidence term : moduleEvidence.terms()) {
                rows.add(new TermRow(moduleEvidence.moduleName(),
                        term.term(),
                        term.tier(),
                        term.occurrences(),
                        term.occurrencesByChannel()));
            }
        }

        Map<String, List<TermRow>> rowsByTerm = new LinkedHashMap<>();

        for (TermRow row : rows) {
            rowsByTerm.computeIfAbsent(row.term(), term -> new ArrayList<>()).add(row);
        }

        List<TermSpan> spans = new ArrayList<>(rowsByTerm.size());

        for (Map.Entry<String, List<TermRow>> entry : rowsByTerm.entrySet()) {
            List<TermRow> termRows = entry.getValue();
            List<String> modules = termRows.stream().map(TermRow::module).distinct().sorted().toList();
            int occurrences = termRows.stream().mapToInt(TermRow::occurrences).sum();

            spans.add(new TermSpan(entry.getKey(), occurrences, modules, dominantTierOf(termRows)));
        }

        return new ProjectVocabulary(List.copyOf(rows), List.copyOf(spans));
    }

    /**
     * @return the tier that holds the most occurrences of the term, and {@code unclassified} on a tie, so
     * that a word the lexicon is unsure about stays visible as a candidate
     */
    private static LexiconTier dominantTierOf(List<TermRow> rows) {
        Map<LexiconTier, Integer> occurrencesByTier = new LinkedHashMap<>();

        for (TermRow row : rows) {
            occurrencesByTier.merge(row.tier(), row.occurrences(), Integer::sum);
        }

        LexiconTier dominant = LexiconTier.UNCLASSIFIED;
        int dominants = -1;

        for (Map.Entry<LexiconTier, Integer> entry : occurrencesByTier.entrySet()) {
            if (entry.getValue() > dominants) {
                dominants = entry.getValue();
                dominant = entry.getKey();
            }
        }

        return dominant;
    }

    public List<String> modules() {
        return rows.stream().map(TermRow::module).distinct().sorted().toList();
    }

    public List<TermRow> rowsOfModule(String module) {
        return rows.stream().filter(row -> row.module().equals(module)).toList();
    }

    public TermSpan spanOf(String term) {
        return spans.stream().filter(span -> span.term().equals(term)).findFirst().orElse(null);
    }

    /**
     * @return the candidate domain terms of a module, most frequent first, ties broken by the term
     */
    public List<TermRow> candidateTermsOfModule(String module) {
        return rowsOfModule(module).stream()
                .filter(row -> row.tier().isDomainCandidate())
                .sorted(Comparator.comparingInt(TermRow::occurrences).reversed()
                        .thenComparing(TermRow::term))
                .toList();
    }

    /**
     * @return the set of candidate domain terms of a module, without the vocabulary of the whole project
     */
    public Set<String> candidateTermSetOfModule(String module, Set<String> ubiquitousTerms) {
        Set<String> terms = new LinkedHashSet<>(candidateTermSetOfModule(module));
        terms.removeAll(ubiquitousTerms);

        return terms;
    }

    /**
     * @return the set of candidate domain terms of a module
     */
    public Set<String> candidateTermSetOfModule(String module) {
        Set<String> terms = new LinkedHashSet<>();

        for (TermRow row : candidateTermsOfModule(module)) {
            terms.add(row.term());
        }

        return terms;
    }

    /**
     * @param ubiquitousModuleCount the lowest number of modules a candidate term has to be used by to count as
     *                              the vocabulary of the whole project
     * @return the terms that many modules use. They are the project's own name, the roots of its packages and
     * the words every layer repeats, so no pair of modules can be told apart by them, and they establish nothing
     */
    public Set<String> ubiquitousTerms(int ubiquitousModuleCount) {
        Set<String> terms = new LinkedHashSet<>();

        for (TermSpan span : ubiquitousSpanTerms(ubiquitousModuleCount)) {
            terms.add(span.term());
        }

        return terms;
    }

    /**
     * @return the project-wide terms of the vocabulary, most widely used first, as the evidence reports them
     */
    public List<TermSpan> ubiquitousSpanTerms(int ubiquitousModuleCount) {
        return spans.stream()
                .filter(span -> span.dominantTier().isDomainCandidate())
                .filter(span -> span.moduleCount() >= ubiquitousModuleCount)
                .sorted(Comparator.comparingInt(TermSpan::moduleCount).reversed()
                        .thenComparing(TermSpan::term))
                .toList();
    }

    /**
     * @return the candidate terms of a module, most frequent first, ties broken by the term, without the terms
     * that are the vocabulary of the whole project
     */
    public List<TermRow> candidateTermsOfModule(String module, Set<String> ubiquitousTerms) {
        return candidateTermsOfModule(module).stream()
                .filter(row -> !ubiquitousTerms.contains(row.term()))
                .toList();
    }

    /**
     * @return the candidate terms of a module that more than one other module uses and that are not the
     * vocabulary of the project, most widely used first
     */
    public List<TermSpan> sharedCandidateTerms(Set<String> ubiquitousTerms) {
        return sharedCandidateTerms().stream()
                .filter(span -> !ubiquitousTerms.contains(span.term()))
                .toList();
    }

    /**
     * @return the candidate terms that more than one module uses, that is the words whose meaning no single
     * module decides alone, most widely used first
     */
    public List<TermSpan> sharedCandidateTerms() {
        return spans.stream()
                .filter(span -> span.dominantTier().isDomainCandidate())
                .filter(span -> span.moduleCount() > 1)
                .sorted(Comparator.comparingInt(TermSpan::moduleCount).reversed()
                        .thenComparing(TermSpan::term))
                .toList();
    }
}
