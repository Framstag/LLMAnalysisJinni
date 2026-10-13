package com.framstag.llmaj.tools.java.language.overview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LexicalOverlapTest {

    @TempDir
    Path tempDir;

    @Test
    void aPairNamesWhatItSharesAndWhatEachSideKeepsToItself() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmArtifact"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmPlugin")));

        LexicalOverlap.PairOverlap pair = LexicalOverlap.of(vocabulary, java.util.Set.of()).pairOf("core", "api");

        assertNotNull(pair);
        assertEquals(List.of("realm"), pair.sharedTerms());
        // The pair is ordered by the module names, so api comes first.
        assertEquals("api", pair.firstModule());
        assertEquals(List.of("plugin"), pair.exclusiveToFirst());
        assertEquals(List.of("artifact"), pair.exclusiveToSecond());
        assertEquals(1.0 / 3.0, pair.overlap(), 1e-9);
    }

    @Test
    void aPairWithoutASharedTermIsReportedInsteadOfOmitted() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "ArtifactPlugin")));

        LexicalOverlap overlap = LexicalOverlap.of(vocabulary, java.util.Set.of());

        assertEquals(1, overlap.pairs().size());
        assertEquals(0.0, overlap.pairs().getFirst().overlap(), 1e-9);
        assertEquals(List.of(), overlap.pairs().getFirst().sharedTerms());
    }

    @Test
    void everyPairOfModulesIsCovered() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmPlugin"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir), "ArtifactPlugin")));

        assertEquals(3, LexicalOverlap.of(vocabulary, java.util.Set.of()).pairs().size());
    }

    @Test
    void aTechnicalTermNeverCountsAsSharedLanguage() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        "RealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        "PluginCache")));

        assertEquals(List.of(), LexicalOverlap.of(vocabulary, java.util.Set.of()).pairs().getFirst().sharedTerms());
    }

    @Test
    void aModuleWithoutVocabularyTakesPartInNoPair() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmCache"),
                OverviewTestData.evidence("empty", OverviewTestData.lexicon(tempDir))));

        assertEquals(List.of(), LexicalOverlap.of(vocabulary, java.util.Set.of()).pairs());
    }

    @Test
    void theSharedLeadingTermsAreTheTermsBothModulesTreatAsTheirOwn() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir),
                        "Realm", "Realm", "Artifact", "Plugin"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "Realm", "Artifact")));

        assertEquals(List.of("artifact", "realm"),
                LexicalOverlap.sharedLeadingTerms(vocabulary, "core", "api", 2, java.util.Set.of()));
        assertEquals(List.of(),
                LexicalOverlap.sharedLeadingTerms(vocabulary, "core", "api", 1, java.util.Set.of()),
                "with a bound of one, core leads the same way as api does not, so they lead with nothing");
        assertEquals(List.of("artifact", "realm"),
                LexicalOverlap.sharedLeadingTerms(vocabulary, "core", "api", 3, java.util.Set.of()),
                "the shared terms of the two modules are realm and artifact");
    }

    @Test
    void aProjectWideTermIsNotSharedLanguage() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "MavenRealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "MavenRealmPlugin")));

        assertEquals(List.of("realm"),
                LexicalOverlap.of(vocabulary, java.util.Set.of("maven")).pairs().getFirst().sharedTerms(),
                "the module name is not a shared language, it is the name of the project");
    }
}
