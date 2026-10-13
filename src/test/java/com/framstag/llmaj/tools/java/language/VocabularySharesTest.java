package com.framstag.llmaj.tools.java.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VocabularySharesTest {

    @TempDir
    Path tempDir;

    @Test
    void theSharesDivideByTheCountedOccurrencesAndLeaveTheNoiseTierOut() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir,
                "impl", "technical", "result", "generic", "get", "noise");

        VocabularyShares shares = VocabularyShares.of(
                VocabularyTestData.occurrences("impl", 4, "result", 2, "get", 10, "realm", 3, "artifact", 1),
                lexicon);

        assertEquals(4, shares.technicalOccurrences());
        assertEquals(2, shares.genericOccurrences());
        assertEquals(10, shares.noiseOccurrences());
        assertEquals(4, shares.domainOccurrences());
        assertEquals(0.4, shares.technicalShare(), 1e-9);
        assertEquals(0.2, shares.genericShare(), 1e-9);
        assertEquals(2, shares.distinctDomainTerms());
        assertEquals(1.0, shares.topTermShare(), 1e-9);
    }

    @Test
    void theTopTermShareCountsTheFiveLeadingTermsOnly() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        VocabularyShares shares = VocabularyShares.of(
                VocabularyTestData.occurrences("realm", 5, "artifact", 4, "plugin", 3, "resolution", 2,
                        "repository", 1, "mojo", 1),
                lexicon);

        assertEquals(6, shares.distinctDomainTerms());
        assertEquals(16, shares.domainOccurrences());
        assertEquals(15.0 / 16.0, shares.topTermShare(), 1e-9);
    }

    @Test
    void tiesAreBrokenByTheTermSoTheValueDoesNotDependOnTheReportOrder() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        VocabularyShares shares = VocabularyShares.of(
                VocabularyTestData.occurrencesOf(new java.util.LinkedHashMap<>(
                        Map.of("zulu", 1, "alpha", 1, "mike", 1, "bravo", 1, "yankee", 1, "charlie", 1))),
                lexicon);

        assertEquals(5.0 / 6.0, shares.topTermShare(), 1e-9);
    }

    @Test
    void aVocabularyWithoutACountedOccurrenceReportsZeroShares() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        VocabularyShares shares = VocabularyShares.of(VocabularyTestData.occurrences(), lexicon);

        assertEquals(0, shares.distinctDomainTerms());
        assertEquals(0.0, shares.technicalShare(), 1e-9);
        assertEquals(0.0, shares.genericShare(), 1e-9);
        assertEquals(0.0, shares.topTermShare(), 1e-9);
    }

    @Test
    void aVocabularyOfNoiseOnlyReportsZeroShares() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "get", "noise", "set", "noise");

        VocabularyShares shares = VocabularyShares.of(VocabularyTestData.occurrences("get", 7, "set", 3),
                lexicon);

        assertEquals(10, shares.noiseOccurrences());
        assertEquals(0.0, shares.technicalShare(), 1e-9);
        assertEquals(0.0, shares.genericShare(), 1e-9);
        assertEquals(0.0, shares.topTermShare(), 1e-9);
    }

    @Test
    void everyTierIsReportedEvenWhenItDidNotOccur() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        Map<LexiconTier, Integer> tierOccurrences = VocabularyShares.tierOccurrences(
                VocabularyTestData.occurrences("realm", 2), lexicon);

        assertEquals(4, tierOccurrences.size());
        assertEquals(2, tierOccurrences.get(LexiconTier.UNCLASSIFIED));
        assertEquals(0, tierOccurrences.get(LexiconTier.NOISE));
        assertEquals(0, tierOccurrences.get(LexiconTier.GENERIC));
    }

    @Test
    void theLeadingTermsAreTheMostFrequentOnesAndTiesAreBrokenByTheTerm() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        List<String> leading = VocabularyShares.leadingDomainTerms(
                VocabularyTestData.occurrences("zulu", 1, "realm", 5, "alpha", 1, "impl", 9), lexicon, 2);

        assertEquals(List.of("realm", "alpha"), leading);
    }

    @Test
    void theTierNamesAreTheOnesTheLexiconUses() throws IOException {
        DomainLexicon lexicon = VocabularyTestData.lexicon(tempDir, "impl", "technical");

        Map<String, Integer> named = VocabularyShares.namedTierOccurrences(
                VocabularyTestData.occurrences("impl", 2, "realm", 1), lexicon);

        assertEquals(2, named.get("technical"));
        assertEquals(1, named.get("unclassified"));
        assertEquals(0, named.get("noise"));
        assertEquals(0, named.get("generic"));
    }
}
