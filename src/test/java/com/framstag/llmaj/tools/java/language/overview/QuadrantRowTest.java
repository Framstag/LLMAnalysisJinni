package com.framstag.llmaj.tools.java.language.overview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The quadrant joins the language two modules share with the imports between them. The tests walk the four
 * combinations of the two axes and the case in which the structural side is unknown.
 */
class QuadrantRowTest {

    private static final int TWO_LEADING_TERMS = 2;

    // A fixture of two modules has no vocabulary of its own to speak of, so nothing is set aside.
    private static final java.util.Set<String> NOT_UBIQUITOUS = java.util.Set.of();

    @TempDir
    Path tempDir;

    private List<QuadrantRow> quadrantOf(ImportMatrix matrix, String... typeNamesPerModule) throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), typeNamesPerModule[0]),
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), typeNamesPerModule[1])));
        LexicalOverlap overlap = LexicalOverlap.of(vocabulary, NOT_UBIQUITOUS);

        return QuadrantRow.of(vocabulary, overlap, matrix, TWO_LEADING_TERMS, NOT_UBIQUITOUS);
    }

    private static ImportMatrix matrixOf(Integer importCount) {
        if (importCount == null) {
            return ImportMatrix.absent();
        }

        return new ImportMatrix(importCount == 0 ? Map.of() : Map.of("api>core", importCount),
                ImportMatrix.REPORT_FILE,
                true);
    }

    @Test
    void aPairThatSharesLanguageWithoutAnImportIsACandidateDomainLeak() throws IOException {
        QuadrantRow row = quadrantOf(matrixOf(0), "RealmCache", "RealmPlugin").getFirst();

        assertEquals(QuadrantRow.CANDIDATE_DOMAIN_LEAK, row.classification());
        assertEquals(0, row.importCount());
        assertEquals(List.of("realm"), row.sharedLeadingTerms());
    }

    @Test
    void aPairThatSharesLanguageAndImportsIsCoupled() throws IOException {
        QuadrantRow row = quadrantOf(matrixOf(7), "RealmCache", "RealmPlugin").getFirst();

        assertEquals(QuadrantRow.COUPLED, row.classification());
        assertEquals(7, row.importCount());
    }

    @Test
    void aPairThatImportsWithoutSharingLanguageIsATranslation() throws IOException {
        QuadrantRow row = quadrantOf(matrixOf(7), "RealmCache", "ArtifactPlugin").getFirst();

        assertEquals(QuadrantRow.TRANSLATION, row.classification());
        assertEquals(List.of(), row.sharedLeadingTerms());
        assertEquals(0.0, row.overlap(), 1e-9);
    }

    @Test
    void aPairWithoutEitherIsIndependent() throws IOException {
        QuadrantRow row = quadrantOf(matrixOf(0), "RealmCache", "ArtifactPlugin").getFirst();

        assertEquals(QuadrantRow.INDEPENDENT, row.classification());
    }

    @Test
    void withoutAnImportMatrixNoPairIsClassifiedByOne() throws IOException {
        QuadrantRow row = quadrantOf(ImportMatrix.absent(), "RealmCache", "RealmPlugin").getFirst();

        assertNull(row.importCount(), "an absent matrix is not an import count of zero");
        assertEquals(QuadrantRow.STRUCTURAL_EVIDENCE_MISSING, row.classification());
    }

    @Test
    void theImportsAreCountedInBothDirections() throws IOException {
        ImportMatrix matrix = new ImportMatrix(Map.of("api>core", 3, "core>api", 4),
                ImportMatrix.REPORT_FILE,
                true);

        QuadrantRow row = QuadrantRow.of(
                ProjectVocabulary.of(List.of(
                        OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmCache"),
                        OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmPlugin"))),
                LexicalOverlap.of(ProjectVocabulary.of(List.of(
                        OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmCache"),
                        OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmPlugin"))), NOT_UBIQUITOUS),
                matrix,
                TWO_LEADING_TERMS,
                NOT_UBIQUITOUS).getFirst();

        assertEquals(7, row.importCount());
    }

    @Test
    void aTermThatIsLeadingInOneModuleOnlyIsNotSharedLanguage() throws IOException {
        // realm is the leading term of both, cache is only leading in core and plugin only in api
        QuadrantRow row = quadrantOf(matrixOf(0), "RealmCache", "RealmPlugin").getFirst();

        assertEquals(1, row.sharedLeadingTerms().size());
        assertEquals(1.0 / 3.0, row.overlap(), 1e-9);
    }

    @Test
    void theClassificationsAreNamedInOnePlace() {
        assertEquals(List.of(QuadrantRow.CANDIDATE_DOMAIN_LEAK, QuadrantRow.TRANSLATION, QuadrantRow.COUPLED,
                        QuadrantRow.INDEPENDENT, QuadrantRow.STRUCTURAL_EVIDENCE_MISSING),
                QuadrantRow.classifications());
    }
}
