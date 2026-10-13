package com.framstag.llmaj.tools.java.language.overview;

import com.framstag.llmaj.tools.common.CsvReportWriter;
import com.framstag.llmaj.tools.java.language.VocabularyChannel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the project-level language evidence.
 *
 * <p>One report type, one file per view, because the views do not share their columns: the term rows, the term
 * rollup, the module pairs, the intruding terms, the quadrant and the glossary candidates are read for
 * different questions and joined by hand in a spreadsheet when a reader wants to check one of them.
 *
 * <pre>
 * DomainLanguageOverview/TermByModule.csv          every module and term with its channels
 * DomainLanguageOverview/TermTotals.csv            every term with its span over the modules
 * DomainLanguageOverview/TermOverlap.csv           every pair of modules with its shared terms
 * DomainLanguageOverview/TermIntrusion.csv         every term a module uses and does not own
 * DomainLanguageOverview/StructuralVsLexical.csv   every pair with its imports, its overlap and its class
 * DomainLanguageOverview/ContextCandidates.csv     the terms worth fixing in a project glossary
 * DomainLanguageOverview/UbiquitousTerms.csv       the vocabulary of the whole project, which marks no boundary
 * </pre>
 *
 * <p>Like the per-module evidence, the files are sorted and carry no wall clock time and no run identifier, so
 * two runs over the same module reports produce byte-identical files.
 */
public final class ContextMapReports {

    public static final String REPORT_TYPE = "DomainLanguageOverview";

    private static final String UNAVAILABLE = "unavailable";

    private ContextMapReports() {
    }

    public static void write(Path workingDirectory, DomainLanguageOverview overview) {
        writeTermByModule(workingDirectory, overview);
        writeTermTotals(workingDirectory, overview);
        writeOverlap(workingDirectory, overview);
        writeIntrusions(workingDirectory, overview);
        writeQuadrant(workingDirectory, overview);
        writeContextCandidates(workingDirectory, overview);
        writeUbiquitousTerms(workingDirectory, overview);
    }

    /**
     * A view that has nothing to report is still written, with its header and no row: the six files are read as
     * one set of evidence, and a missing file would look like an analysis that never ran.
     */
    private static void write(Path workingDirectory, String view, String[] header, List<String[]> rows) {
        if (rows.isEmpty()) {
            CsvReportWriter.writeCsvHeader(workingDirectory, file(view), header);
            return;
        }

        CsvReportWriter.writeCsv(workingDirectory, file(view), header, rows);
    }

    private static void writeTermByModule(Path workingDirectory, DomainLanguageOverview overview) {
        List<String[]> rows = new ArrayList<>(overview.vocabulary().rows().size());

        for (ProjectVocabulary.TermRow row : overview.vocabulary().rows()) {
            rows.add(new String[]{row.module(),
                    row.term(),
                    row.tier().getValue(),
                    String.valueOf(row.occurrences()),
                    String.valueOf(row.getOccurrences(VocabularyChannel.TYPE)),
                    String.valueOf(row.getOccurrences(VocabularyChannel.METHOD)),
                    String.valueOf(row.getOccurrences(VocabularyChannel.METHOD_VERB)),
                    String.valueOf(row.getOccurrences(VocabularyChannel.FIELD)),
                    String.valueOf(row.getOccurrences(VocabularyChannel.PACKAGE)),
                    String.valueOf(row.getOccurrences(VocabularyChannel.DOC))});
        }

        write(workingDirectory, "TermByModule",
                new String[]{"Module", "Term", "Tier", "Occurrences", "TypeOccurrences", "MethodOccurrences",
                        "MethodVerbOccurrences", "FieldOccurrences", "PackageOccurrences", "DocOccurrences"},
                rows);
    }

    private static void writeTermTotals(Path workingDirectory, DomainLanguageOverview overview) {
        List<String[]> rows = new ArrayList<>(overview.vocabulary().spans().size());
        List<ProjectVocabulary.TermSpan> spans = new ArrayList<>(overview.vocabulary().spans());

        spans.sort(Comparator.comparingInt(ProjectVocabulary.TermSpan::moduleCount).reversed()
                .thenComparing(ProjectVocabulary.TermSpan::term));

        for (ProjectVocabulary.TermSpan span : spans) {
            rows.add(new String[]{span.term(),
                    String.valueOf(span.occurrences()),
                    String.valueOf(span.moduleCount()),
                    String.join(" ", span.modules()),
                    span.dominantTier().getValue()});
        }

        write(workingDirectory, "TermTotals",
                new String[]{"Term", "Occurrences", "ModuleCount", "Modules", "DominantTier"}, rows);
    }

    private static void writeOverlap(Path workingDirectory, DomainLanguageOverview overview) {
        List<String[]> rows = new ArrayList<>(overview.overlap().pairs().size());

        for (LexicalOverlap.PairOverlap pair : overview.overlap().pairs()) {
            rows.add(new String[]{pair.firstModule(),
                    pair.secondModule(),
                    String.join(" ", pair.sharedTerms()),
                    String.valueOf(pair.sharedTerms().size()),
                    format(pair.overlap()),
                    String.join(" ", pair.exclusiveToFirst()),
                    String.join(" ", pair.exclusiveToSecond())});
        }

        write(workingDirectory, "TermOverlap",
                new String[]{"FromModule", "ToModule", "SharedTerms", "SharedTermCount", "Overlap",
                        "ExclusiveFrom", "ExclusiveTo"},
                rows);
    }

    private static void writeIntrusions(Path workingDirectory, DomainLanguageOverview overview) {
        List<String[]> rows = new ArrayList<>(overview.intrusions().size());

        for (TermIntrusion intrusion : overview.intrusions()) {
            rows.add(new String[]{intrusion.module(),
                    intrusion.term(),
                    String.join(" ", intrusion.ownerModules()),
                    String.valueOf(intrusion.occurrences()),
                    format(intrusion.domainShare()),
                    format(intrusion.ownersCarryTerm())});
        }

        write(workingDirectory, "TermIntrusion",
                new String[]{"Module", "Term", "OwnerModules", "Occurrences", "DomainShare", "OwnersCarryTerm"},
                rows);
    }

    private static void writeQuadrant(Path workingDirectory, DomainLanguageOverview overview) {
        List<String[]> rows = new ArrayList<>(overview.quadrant().size());

        for (QuadrantRow row : overview.quadrant()) {
            rows.add(new String[]{row.firstModule(),
                    row.secondModule(),
                    row.importCount() == null ? UNAVAILABLE : String.valueOf(row.importCount()),
                    format(row.overlap()),
                    String.join(" ", row.sharedLeadingTerms()),
                    row.classification()});
        }

        write(workingDirectory, "StructuralVsLexical",
                new String[]{"FromModule", "ToModule", "ImportCount", "LexicalOverlap", "SharedLeadingTerms",
                        "Classification"},
                rows);
    }

    /**
     * The terms a glossary should settle first: candidate domain terms that more than one module uses. A term
     * only one module uses is that module's own business.
     */
    private static void writeContextCandidates(Path workingDirectory, DomainLanguageOverview overview) {
        Map<String, Boolean> ownersCarryTermByTerm = new LinkedHashMap<>();

        for (TermIntrusion intrusion : overview.intrusions()) {
            if (intrusion.ownersCarryTerm() != null) {
                ownersCarryTermByTerm.merge(intrusion.term(), intrusion.ownersCarryTerm(), Boolean::logicalOr);
            }
        }

        List<String[]> rows = new ArrayList<>();

        for (ProjectVocabulary.TermSpan span : overview.vocabulary().sharedCandidateTerms()) {
            rows.add(new String[]{span.term(),
                    String.valueOf(span.occurrences()),
                    String.valueOf(span.moduleCount()),
                    String.join(" ", span.modules()),
                    span.dominantTier().getValue(),
                    format(ownersCarryTermByTerm.get(span.term()))});
        }

        write(workingDirectory, "ContextCandidates",
                new String[]{"Term", "Occurrences", "ModuleCount", "Modules", "DominantTier",
                        "InOwnersDescription"},
                rows);
    }

    /**
     * The vocabulary of the whole project: the terms so many modules use that no pair of modules can be told
     * apart by them. They are kept out of the lexical axis of the quadrant and reported here instead, so that a
     * reader can see what the analysis set aside and why.
     */
    private static void writeUbiquitousTerms(Path workingDirectory, DomainLanguageOverview overview) {
        List<String[]> rows = new ArrayList<>();

        for (ProjectVocabulary.TermSpan span : overview.ubiquitousTerms()) {
            rows.add(new String[]{span.term(),
                    String.valueOf(span.occurrences()),
                    String.valueOf(span.moduleCount()),
                    String.join(" ", span.modules()),
                    span.dominantTier().getValue()});
        }

        write(workingDirectory, "UbiquitousTerms",
                new String[]{"Term", "Occurrences", "ModuleCount", "Modules", "DominantTier"},
                rows);
    }

    private static String file(String view) {
        return REPORT_TYPE + "/" + view + ".csv";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String format(Boolean value) {
        return value == null ? UNAVAILABLE : value.toString();
    }
}
