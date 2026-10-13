package com.framstag.llmaj.tools.java.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextAlignmentTest {

    @TempDir
    Path tempDir;

    @Test
    void theCoverageCountsTheDescriptionWordsTheCodeUses() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "the", "noise", "of", "noise",
                "impl", "technical");

        TextAlignment alignment = TextAlignment.of(
                VocabularyTestData.occurrences("realm", 3, "cache", 2, "impl", 4),
                "The realm cache of the project",
                lexicon,
                10);

        // 'realm' and 'cache' are used, 'project' is stated but not used, and the two noise words carry no concept.
        assertEquals(2.0 / 3.0, alignment.purposeCoverage(), 1e-9);
        assertEquals(List.of("project"), alignment.absentPurposeTerms());
    }

    @Test
    void anAbsentDescriptionReportsNoCoverageInsteadOfZero() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        TextAlignment alignment = TextAlignment.of(VocabularyTestData.occurrences("realm", 3), null, lexicon, 10);

        assertNull(alignment.purposeCoverage());
        assertNull(alignment.codeCoverage());
        assertEquals(List.of(), alignment.absentPurposeTerms());
        assertEquals(List.of(), alignment.absentCodeTerms());
    }

    @Test
    void anEmptyDescriptionReportsNoCoverageInsteadOfZero() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "the", "noise", "of", "noise", "and",
                "noise");

        TextAlignment alignment = TextAlignment.of(VocabularyTestData.occurrences("realm", 3), "the of and",
                lexicon, 10);

        assertNull(alignment.purposeCoverage());
        assertNull(alignment.codeCoverage());
    }

    @Test
    void theAbsentCodeTermsAreTheLeadingOnesTheDescriptionDoesNotMention() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "the", "noise", "is", "noise",
                "impl", "technical");

        TextAlignment alignment = TextAlignment.of(
                VocabularyTestData.occurrences("realm", 5, "artifact", 4, "plugin", 3, "impl", 9),
                "The realm is resolved",
                lexicon,
                10);

        assertEquals(List.of("artifact", "plugin"), alignment.absentCodeTerms());
        assertEquals(1.0 / 3.0, alignment.codeCoverage(), 1e-9);
    }

    @Test
    void aCandidateTermLeadsTheAbsentPurposeTerms() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "the", "noise", "impl", "technical",
                "result", "generic");

        TextAlignment alignment = TextAlignment.of(
                VocabularyTestData.occurrences("realm", 3),
                "The impl result for the mojo",
                lexicon,
                10);

        assertEquals(List.of("mojo", "impl", "result"), alignment.absentPurposeTerms());
    }

    @Test
    void theAbsenceListsRespectTheirLimit() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        TextAlignment alignment = TextAlignment.of(
                VocabularyTestData.occurrences("alpha", 5, "bravo", 4, "charlie", 3),
                "The delta is resolved",
                lexicon,
                1);

        assertEquals(List.of("delta"), alignment.absentPurposeTerms());
        assertEquals(List.of("alpha"), alignment.absentCodeTerms());
    }

    @Test
    void theDocumentationOverlapComparesNamesWithDocumentation() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "the", "noise", "impl", "technical");
        TermOccurrences classDoc = new TermOccurrences();
        classDoc.add(VocabularyChannel.TYPE);
        classDoc.add(VocabularyChannel.DOC);

        Map<String, TermOccurrences> occurrencesByTerm = new java.util.LinkedHashMap<>();
        occurrencesByTerm.put("realm", classDoc);

        TermOccurrences nameOnly = new TermOccurrences();
        nameOnly.add(VocabularyChannel.FIELD);
        occurrencesByTerm.put("cache", nameOnly);

        TermOccurrences docOnly = new TermOccurrences();
        docOnly.add(VocabularyChannel.DOC);
        occurrencesByTerm.put("resolved", docOnly);

        TextAlignment alignment = TextAlignment.of(occurrencesByTerm, "The realm is resolved", lexicon, 10);

        // shared: realm. identifier side: realm, cache. documentation side: realm, resolved.
        assertEquals(1.0 / 3.0, alignment.documentationOverlap(), 1e-9);
    }

    @Test
    void aModuleWithoutDocumentationReportsNoDocumentationOverlap() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        TextAlignment alignment = TextAlignment.of(
                VocabularyTestData.occurrences("realm", 3),
                "The realm cache",
                lexicon,
                10);

        assertNull(alignment.documentationOverlap());
    }

    @Test
    void aModuleWithoutAnyNameReportsNoDocumentationOverlap() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "the", "noise", "impl", "technical");
        TermOccurrences docOnly = new TermOccurrences();
        docOnly.add(VocabularyChannel.DOC);

        Map<String, TermOccurrences> occurrencesByTerm = new java.util.LinkedHashMap<>();
        occurrencesByTerm.put("realm", docOnly);

        TextAlignment alignment = TextAlignment.of(occurrencesByTerm, "The realm cache", lexicon, 10);

        assertNull(alignment.documentationOverlap());
        assertTrue(alignment.absentPurposeTerms().contains("cache"));
    }
}
