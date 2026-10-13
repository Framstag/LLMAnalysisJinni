package com.framstag.llmaj.tools.java.language.overview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module establishes a term when the term is one of its leading candidate terms. The tests use a bound of one
 * leading term so that each fixture module owns exactly the term its name puts first, which is what makes a
 * borrowing visible.
 */
class TermIntrusionTest {

    private static final int ONE_LEADING_TERM = 1;

    // A fixture of two modules has no vocabulary of its own to speak of, so nothing is set aside.
    private static final java.util.Set<String> NOT_UBIQUITOUS = java.util.Set.of();

    @TempDir
    Path tempDir;

    @Test
    void aTermAnotherModuleEstablishesIsReportedWithItsOwner() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                // repository establishes realm, web establishes artifact
                OverviewTestData.evidence("repository", OverviewTestData.lexicon(tempDir), "RealmRepository"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir), "ArtifactPageRealm")));

        List<TermIntrusion> intrusions = TermIntrusion.of(vocabulary, Map.of(), ONE_LEADING_TERM, NOT_UBIQUITOUS);

        assertTrue(intrusions.stream().anyMatch(intrusion -> intrusion.module().equals("web")
                        && intrusion.term().equals("realm")
                        && intrusion.ownerModules().equals(List.of("repository"))),
                intrusions.toString());
    }

    @Test
    void aTermTheModuleEstablishesItselfIsNeverAnIntrusion() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("repository", OverviewTestData.lexicon(tempDir), "RealmRepository"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir), "ArtifactPageRealm")));

        List<TermIntrusion> intrusions = TermIntrusion.of(vocabulary, Map.of(), ONE_LEADING_TERM, NOT_UBIQUITOUS);

        assertFalse(intrusions.stream().anyMatch(intrusion -> intrusion.term().equals("artifact")),
                "every module owns the term it establishes, so artifact is borrowed from nobody");
        assertFalse(intrusions.stream()
                        .anyMatch(intrusion -> intrusion.module().equals("repository")),
                "the repository establishes realm and borrows nothing");
    }

    @Test
    void theShareIsThePartOfTheUsingModulesDomainVocabularyTheTermHolds() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("repository", OverviewTestData.lexicon(tempDir), "RepositoryRealm"),
                // web leads with its own name, holds realm four times and artifact once, so realm holds four of
                // the ten candidate occurrences of the module without being established by it
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir),
                        OverviewTestData.repeats("web", 5) + "Artifact"
                                + OverviewTestData.repeats("realm", 4))));

        TermIntrusion realm = TermIntrusion.of(vocabulary, Map.of(), ONE_LEADING_TERM, NOT_UBIQUITOUS).stream()
                .filter(intrusion -> intrusion.module().equals("web"))
                .filter(intrusion -> intrusion.term().equals("realm"))
                .findFirst()
                .orElseThrow();

        assertEquals(4, realm.occurrences());
        assertEquals(4.0 / 10.0, realm.domainShare(), 1e-9);
    }

    @Test
    void theOwnerWithoutADescriptionReportsTheOwnershipAsUnknown() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("repository", OverviewTestData.lexicon(tempDir), "RealmRepository"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir), "ArtifactPageRealm")));

        List<TermIntrusion> intrusions = TermIntrusion.of(vocabulary, Map.of(), ONE_LEADING_TERM, NOT_UBIQUITOUS);

        assertNull(intrusions.getFirst().ownersCarryTerm(),
                "a module that was never described is not a module that does not use the word");
    }

    @Test
    void aDescribedOwnerSaysWhetherItsDescriptionCarriesTheTerm() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("repository", OverviewTestData.lexicon(tempDir), "RealmRepository"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir), "ArtifactPageRealm")));

        assertTrue(TermIntrusion.of(vocabulary, Map.of("repository", "The realm of the repository"), 1, NOT_UBIQUITOUS)
                .getFirst()
                .ownersCarryTerm());
        assertFalse(TermIntrusion.of(vocabulary, Map.of("repository", "Stores artifacts"), 1, NOT_UBIQUITOUS)
                .getFirst()
                .ownersCarryTerm());
    }

    @Test
    void aPluralDoesNotCountAsTheOwnerNamingTheTerm() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                OverviewTestData.evidence("repository", OverviewTestData.lexicon(tempDir), "RealmRepository"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir), "ArtifactPageRealm")));

        assertFalse(TermIntrusion.of(vocabulary, Map.of("repository", "Stores realms of artifacts"), 1, NOT_UBIQUITOUS)
                .getFirst()
                .ownersCarryTerm());
    }

    @Test
    void theTermsOfTheLargerShareLeadTheList() throws IOException {
        ProjectVocabulary vocabulary = ProjectVocabulary.of(List.of(
                // the repository establishes artifact and realm, the web module establishes page and web
                OverviewTestData.evidence("repository", OverviewTestData.lexicon(tempDir),
                        "RepositoryArtifactRealm"),
                OverviewTestData.evidence("web", OverviewTestData.lexicon(tempDir),
                        OverviewTestData.repeats("web", 3) + OverviewTestData.repeats("page", 2)
                                + OverviewTestData.repeats("realm", 2) + "Artifact")));

        List<TermIntrusion> intrusions = TermIntrusion.of(vocabulary, Map.of(), 2, NOT_UBIQUITOUS).stream()
                .filter(intrusion -> intrusion.module().equals("web"))
                .toList();

        assertEquals("realm", intrusions.getFirst().term(), "realm is borrowed twice, artifact once");
        assertTrue(intrusions.getFirst().domainShare() > intrusions.getLast().domainShare());
    }
}
