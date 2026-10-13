package com.framstag.llmaj.tools.java.language;

import com.framstag.llmaj.tools.common.CsvReportWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the vocabulary evidence of a module to disk.
 *
 * <p>Three report types, one row per fact each, so that a reader can sort, filter and check by hand what the
 * model reasoned about:
 *
 * <pre>
 * DomainVocabulary/&lt;module&gt;.csv        every distinct term with its tier and its channel counts
 * NamingCohesion/&lt;module&gt;.csv         every pair of build units with its overlap and its reference flag
 * DomainLanguageMetrics/&lt;module&gt;.csv   one row per metric and its value
 * </pre>
 *
 * <p>The term file holds the terms the metrics drop as well. That is the point: a term that the lexicon
 * misclassifies has to stay findable, and a metric whose input is invisible is a number nobody can check.
 * Rows are sorted and no wall clock time and no run identifier is written, so two runs over the same report
 * produce byte-identical files and a difference between two files is a real difference.
 */
public final class DomainVocabularyReports {

    public static final String TERMS_REPORT_TYPE = "DomainVocabulary";
    public static final String COHESION_REPORT_TYPE = "NamingCohesion";
    public static final String METRICS_REPORT_TYPE = "DomainLanguageMetrics";

    /**
     * The text a value carries when the metric could not be computed. A module without a description has no
     * coverage, which is not the same as a coverage of zero.
     */
    private static final String UNAVAILABLE = "unavailable";

    private static final String[] TERM_HEADER = {"Term", "Tier", "Occurrences", "TypeOccurrences",
            "MethodOccurrences", "MethodVerbOccurrences", "FieldOccurrences", "PackageOccurrences",
            "DocOccurrences"};

    private static final String[] COHESION_HEADER = {"Class", "OtherClass", "Linked", "Jaccard", "SharedTerms"};

    private DomainVocabularyReports() {
    }

    public static void write(Path workingDirectory, ModuleLanguageEvidence evidence) {
        String moduleName = evidence.moduleName();

        writeTerms(workingDirectory, moduleName, evidence);
        writeCohesion(workingDirectory, moduleName, evidence);
        writeMetrics(workingDirectory, moduleName, evidence);
    }

    private static void writeTerms(Path workingDirectory, String moduleName, ModuleLanguageEvidence evidence) {
        List<String[]> rows = new ArrayList<>(evidence.terms().size());

        for (ModuleLanguageEvidence.TermEvidence term : evidence.terms()) {
            rows.add(new String[]{term.term(),
                    term.tier().getValue(),
                    String.valueOf(term.occurrences()),
                    String.valueOf(term.getOccurrences(VocabularyChannel.TYPE)),
                    String.valueOf(term.getOccurrences(VocabularyChannel.METHOD)),
                    String.valueOf(term.getOccurrences(VocabularyChannel.METHOD_VERB)),
                    String.valueOf(term.getOccurrences(VocabularyChannel.FIELD)),
                    String.valueOf(term.getOccurrences(VocabularyChannel.PACKAGE)),
                    String.valueOf(term.getOccurrences(VocabularyChannel.DOC))});
        }

        CsvReportWriter.writeCsv(workingDirectory, reportFile(TERMS_REPORT_TYPE, moduleName), TERM_HEADER, rows);
    }

    private static void writeCohesion(Path workingDirectory, String moduleName, ModuleLanguageEvidence evidence) {
        List<String[]> rows = new ArrayList<>(evidence.cohesion().pairs().size());

        for (NamingCohesion.ClassPairOverlap pair : evidence.cohesion().pairs()) {
            rows.add(new String[]{pair.firstBuildUnit(),
                    pair.secondBuildUnit(),
                    String.valueOf(pair.linked()),
                    format(pair.overlap()),
                    String.join(" ", pair.sharedTerms())});
        }

        CsvReportWriter.writeCsv(workingDirectory, reportFile(COHESION_REPORT_TYPE, moduleName), COHESION_HEADER,
                rows);
    }

    private static void writeMetrics(Path workingDirectory, String moduleName, ModuleLanguageEvidence evidence) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"lexiconVersion", evidence.lexiconVersion()});
        rows.add(new String[]{"candidateTerms", String.valueOf(evidence.candidateTerms())});
        rows.add(new String[]{"minimumCandidateTerms", String.valueOf(evidence.minimumCandidateTerms())});
        rows.add(new String[]{"sufficientEvidence", String.valueOf(evidence.sufficientEvidence())});

        if (evidence.sufficientEvidence()) {
            VocabularyShares shares = evidence.shares();
            TextAlignment alignment = evidence.alignment();
            NamingCohesion cohesion = evidence.cohesion();

            rows.add(new String[]{"technicalOccurrences", String.valueOf(shares.technicalOccurrences())});
            rows.add(new String[]{"genericOccurrences", String.valueOf(shares.genericOccurrences())});
            rows.add(new String[]{"noiseOccurrences", String.valueOf(shares.noiseOccurrences())});
            rows.add(new String[]{"domainOccurrences", String.valueOf(shares.domainOccurrences())});
            rows.add(new String[]{"technicalShare", format(shares.technicalShare())});
            rows.add(new String[]{"genericShare", format(shares.genericShare())});
            rows.add(new String[]{"topTermShare", format(shares.topTermShare())});
            rows.add(new String[]{"namingPairs", String.valueOf(cohesion.pairs().size())});
            rows.add(new String[]{"namingCohesion", format(cohesion.value())});
            rows.add(new String[]{"namingCohesionLinked", format(cohesion.linkedOverlap())});
            rows.add(new String[]{"namingCohesionUnlinked", format(cohesion.unlinkedOverlap())});
            rows.add(new String[]{"purposeCoverage", format(alignment.purposeCoverage())});
            rows.add(new String[]{"codeCoverage", format(alignment.codeCoverage())});
            rows.add(new String[]{"documentationOverlap", format(alignment.documentationOverlap())});
            rows.add(new String[]{"absentPurposeTerms", String.join(" ", alignment.absentPurposeTerms())});
            rows.add(new String[]{"absentCodeTerms", String.join(" ", alignment.absentCodeTerms())});
        }

        CsvReportWriter.writeCsv(workingDirectory, reportFile(METRICS_REPORT_TYPE, moduleName),
                new String[]{"Metric", "Value"}, rows);
    }

    private static String reportFile(String reportType, String moduleName) {
        return reportType + "/" + CsvReportWriter.sanitizeModuleName(moduleName) + ".csv";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String format(Double value) {
        return value == null ? UNAVAILABLE : format(value.doubleValue());
    }

    /**
     * @return the channel counts of a term, keyed by the channel name, for the compact result the model
     * reads
     */
    public static Map<String, Integer> namedChannelOccurrences(ModuleLanguageEvidence.TermEvidence term) {
        Map<String, Integer> result = new LinkedHashMap<>();

        for (VocabularyChannel channel : VocabularyChannel.values()) {
            int occurrences = term.getOccurrences(channel);

            if (occurrences > 0) {
                result.put(channel.getValue(), occurrences);
            }
        }

        return result;
    }
}
