package com.framstag.llmaj.tools.java.language.overview;

import com.framstag.llmaj.tools.java.language.ModuleLanguageEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMapReportsTest {

    @TempDir
    Path tempDir;

    private DomainLanguageOverview overviewOf() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        "RealmCache"),
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        "RealmArtifact")));
        LexicalOverlap overlap = LexicalOverlap.of(vocabulary, NOT_UBIQUITOUS);
        ImportMatrix matrix = new ImportMatrix(Map.of("api>core", 5), ImportMatrix.REPORT_FILE, true);

        return new DomainLanguageOverview(vocabulary,
                overlap,
                TermIntrusion.of(vocabulary, Map.of("api", "The realm of the api"), 1, NOT_UBIQUITOUS),
                QuadrantRow.of(vocabulary, overlap, matrix, 2, NOT_UBIQUITOUS),
                List.of(),
                matrix);
    }

    // A fixture of two modules has no vocabulary of its own to speak of, so nothing is set aside here.
    private static final Set<String> NOT_UBIQUITOUS = Set.of();

    private Map<String, List<String>> readEvidence() throws IOException {
        Map<String, List<String>> evidence = new LinkedHashMap<>();
        Path directory = tempDir.resolve(ContextMapReports.REPORT_TYPE);

        try (var files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                evidence.put(file.getFileName().toString(), Files.readAllLines(file, StandardCharsets.UTF_8));
            }
        }

        return evidence;
    }

    @Test
    void everyViewIsWrittenAsItsOwnFile() throws IOException {
        ContextMapReports.write(tempDir, overviewOf());

        assertEquals(List.of("ContextCandidates.csv", "StructuralVsLexical.csv", "TermByModule.csv",
                        "TermIntrusion.csv", "TermOverlap.csv", "TermTotals.csv", "UbiquitousTerms.csv"),
                readEvidence().keySet().stream().toList());
        assertEquals(7, readEvidence().size());
    }

    @Test
    void theTermRowsCarryTheChannelsAndTheTierOfEveryModule() throws IOException {
        ContextMapReports.write(tempDir, overviewOf());

        List<String> rows = readEvidence().get("TermByModule.csv");

        assertEquals("Module,Term,Tier,Occurrences,TypeOccurrences,MethodOccurrences,MethodVerbOccurrences,"
                + "FieldOccurrences,PackageOccurrences,DocOccurrences", rows.getFirst());
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("api,cache,technical,")),
                "a technical term stays in the evidence with its tier");
        assertEquals(5, rows.size(), "api has cache and realm, core has cache, realm and artifact");
    }

    @Test
    void theTermTotalsNameTheSpanOfEveryTerm() throws IOException {
        ContextMapReports.write(tempDir, overviewOf());

        List<String> rows = readEvidence().get("TermTotals.csv");

        assertEquals("Term,Occurrences,ModuleCount,Modules,DominantTier", rows.getFirst());
        assertTrue(rows.stream().anyMatch(row -> row.equals("realm,2,2,api core,unclassified")), rows.toString());
    }

    @Test
    void theOverlapViewNamesTheSharedAndTheExclusiveTerms() throws IOException {
        ContextMapReports.write(tempDir, overviewOf());

        List<String> rows = readEvidence().get("TermOverlap.csv");

        assertEquals("FromModule,ToModule,SharedTerms,SharedTermCount,Overlap,ExclusiveFrom,ExclusiveTo",
                rows.getFirst());
        assertEquals("api,core,realm,1,0.5000,,artifact", rows.get(1),
                "api keeps nothing to itself, core keeps artifact");
    }

    @Test
    void theIntrusionViewNamesTheOwnerAndTheShare() throws IOException {
        ContextMapReports.write(tempDir, overviewOf());

        List<String> rows = readEvidence().get("TermIntrusion.csv");

        assertEquals("Module,Term,OwnerModules,Occurrences,DomainShare,OwnersCarryTerm", rows.getFirst());
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("core,realm,api,1,0.5000,true")), rows.toString());
    }

    @Test
    void theQuadrantViewCarriesBothAxesAndTheClassification() throws IOException {
        ContextMapReports.write(tempDir, overviewOf());

        List<String> rows = readEvidence().get("StructuralVsLexical.csv");

        assertEquals("FromModule,ToModule,ImportCount,LexicalOverlap,SharedLeadingTerms,Classification",
                rows.getFirst());
        assertTrue(rows.get(1).endsWith(",coupled") || rows.get(1).endsWith(",candidate domain leak"),
                rows.get(1));
    }

    @Test
    void anUnknownStructuralSideIsWrittenAsUnavailable() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmCache"),
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmArtifact")));
        LexicalOverlap overlap = LexicalOverlap.of(vocabulary, NOT_UBIQUITOUS);

        ContextMapReports.write(tempDir, new DomainLanguageOverview(vocabulary,
                overlap,
                List.of(),
                QuadrantRow.of(vocabulary, overlap, ImportMatrix.absent(), 2, NOT_UBIQUITOUS),
                List.of(),
                ImportMatrix.absent()));

        List<String> rows = readEvidence().get("StructuralVsLexical.csv");

        assertTrue(rows.get(1).contains(",unavailable,"), rows.get(1));
        assertTrue(rows.get(1).endsWith(",structural evidence missing"), rows.get(1));
    }

    @Test
    void twoRunsProduceIdenticalFiles() throws IOException {
        DomainLanguageOverview overview = overviewOf();

        ContextMapReports.write(tempDir, overview);
        Map<String, List<String>> firstRun = readEvidence();

        ContextMapReports.write(tempDir, overview);
        Map<String, List<String>> secondRun = readEvidence();

        assertEquals(firstRun, secondRun);
        assertTrue(secondRun.values().stream().flatMap(List::stream).noneMatch(row -> row.matches(".*\\d{4}-\\d{2}-\\d{2}.*")),
                "no wall clock time reaches the evidence");
    }

    @Test
    void aModuleWithoutAVocabularyWritesNoRow() throws IOException {
        ModuleLanguageEvidence empty = OverviewTestData.evidence("empty", OverviewTestData.lexicon(tempDir));

        ContextMapReports.write(tempDir, OverviewTestData.overview(2, Map.of(), empty));

        List<String> rows = readEvidence().get("TermByModule.csv");

        assertEquals(1, rows.size(), "the file holds its header only");
    }
}
