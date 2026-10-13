package com.framstag.llmaj.tools.java.language.overview;

import com.framstag.llmaj.tools.java.language.LexiconTier;
import com.framstag.llmaj.tools.java.language.ModuleLanguageEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectVocabularyTest {

    @TempDir
    Path tempDir;

    @Test
    void oneRowPerModuleAndTermAndNeverAMergedTerm() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(java.util.List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmService")));

        assertEquals(4, vocabulary.rows().size(), "realm occurs twice and is still two rows");
        assertEquals(2, vocabulary.rowsOfModule("core").size());
        assertEquals(2, vocabulary.rowsOfModule("api").size());
        assertEquals(java.util.List.of("api", "core"), vocabulary.modules());
    }

    @Test
    void theSpanOfATermNamesEveryModuleThatUsesIt() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(java.util.List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmService")));

        ProjectVocabulary.TermSpan realm = vocabulary.spanOf("realm");

        assertEquals(2, realm.moduleCount());
        assertEquals(java.util.List.of("api", "core"), realm.modules());
        assertEquals(2, realm.occurrences());
        assertEquals(LexiconTier.UNCLASSIFIED, realm.dominantTier());
    }

    @Test
    void theOccurrencesOfATermSumOverTheModules() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(java.util.List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir),
                        OverviewTestData.repeats("realm", 3)),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir),
                        OverviewTestData.repeats("realm", 2))));

        assertEquals(5, vocabulary.spanOf("realm").occurrences());
        assertEquals(2, vocabulary.spanOf("realm").moduleCount());
    }

    @Test
    void theDominantTierIsTheOneThatHoldsMostOccurrences() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(java.util.List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        OverviewTestData.repeats("cache", 4)),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        "CacheRealmRealm")));

        assertEquals(LexiconTier.UNCLASSIFIED, vocabulary.spanOf("realm").dominantTier(),
                "realm is a candidate everywhere it occurs");
        assertEquals(LexiconTier.TECHNICAL, vocabulary.spanOf("cache").dominantTier(),
                "four technical occurrences beat the single one of api");
    }

    @Test
    void aTermThatOnlyOneModuleUsesIsNotASharedTerm() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(java.util.List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "ArtifactService")));

        assertEquals(java.util.List.of(), vocabulary.sharedCandidateTerms());
    }

    @Test
    void theSharedTermsAreOrderedByHowWidelyTheyAreUsed() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(java.util.List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir), "RealmCacheArtifact"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir), "RealmArtifact"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir), "ArtifactService")));

        java.util.List<ProjectVocabulary.TermSpan> shared = vocabulary.sharedCandidateTerms();

        assertEquals("artifact", shared.get(0).term(), "artifact is used by all three modules");
        assertEquals("realm", shared.get(1).term(), "realm is used by two");
        assertEquals(3, shared.get(0).moduleCount());
    }

    @Test
    void aTechnicalTermIsNeverASharedDomainTerm() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(java.util.List.of(
                OverviewTestData.evidence("core", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        "RealmCache"),
                OverviewTestData.evidence("api", OverviewTestData.lexicon(tempDir, "cache", "technical"),
                        "ArtifactCache")));

        assertEquals(java.util.List.of("realm"), vocabulary.candidateTermSetOfModule("core").stream().toList());
        assertTrue(vocabulary.sharedCandidateTerms().isEmpty());
    }
}
