package com.framstag.llmaj.tools.java.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainLexiconTest {

    @TempDir
    Path tempDir;

    private Path writeLexicon(String... lines) throws IOException {
        Path path = tempDir.resolve("domain_lexicon.csv");
        Files.write(path, java.util.List.of(lines));

        return path;
    }

    @Test
    void aListedTermCarriesItsTier() throws Exception {
        DomainLexicon lexicon = DomainLexicon.load(writeLexicon(
                "Version,7",
                "Term,Tier,Note",
                "impl,technical,implementation suffix",
                "result,generic,carries no domain",
                "get,noise,accessor"));

        assertEquals("7", lexicon.getVersion());
        assertEquals(3, lexicon.size());
        assertEquals(LexiconTier.TECHNICAL, lexicon.tierOf("impl"));
        assertEquals(LexiconTier.GENERIC, lexicon.tierOf("result"));
        assertEquals(LexiconTier.NOISE, lexicon.tierOf("get"));
    }

    @Test
    void anUnlistedTermIsACandidateDomainTerm() throws Exception {
        DomainLexicon lexicon = DomainLexicon.load(writeLexicon(
                "Version,1",
                "Term,Tier,Note",
                "impl,technical,implementation suffix"));

        assertEquals(LexiconTier.UNCLASSIFIED, lexicon.tierOf("realm"));
        assertTrue(lexicon.tierOf("realm").isDomainCandidate());
        assertEquals(LexiconTier.UNCLASSIFIED, lexicon.tierOf(null));
    }

    @Test
    void anEmptyLexiconMakesEveryTermACandidate() throws Exception {
        DomainLexicon lexicon = DomainLexicon.load(writeLexicon(
                "Version,1",
                "Term,Tier,Note"));

        assertEquals(0, lexicon.size());
        assertEquals(LexiconTier.UNCLASSIFIED, lexicon.tierOf("realm"));
    }

    @Test
    void commentsAndBlankLinesAreIgnored() throws Exception {
        DomainLexicon lexicon = DomainLexicon.load(writeLexicon(
                "# a comment before the version",
                "",
                "Version,2",
                "# a comment before the header",
                "Term,Tier,Note",
                "",
                "the,noise,an English stop word"));

        assertEquals("2", lexicon.getVersion());
        assertEquals(LexiconTier.NOISE, lexicon.tierOf("the"));
    }

    @Test
    void anUnclassifiedRowIsRejected() throws Exception {
        Path path = writeLexicon(
                "Version,1",
                "Term,Tier,Note",
                "realm,unclassified,a candidate by definition");

        IOException error = assertThrows(IOException.class, () -> DomainLexicon.load(path));

        assertTrue(error.getMessage().contains("unclassified"), error.getMessage());
    }

    @Test
    void anUnknownTierIsRejectedWithTheLineNumber() throws Exception {
        Path path = writeLexicon(
                "Version,1",
                "Term,Tier,Note",
                "impl,plumbing,not a tier");

        IOException error = assertThrows(IOException.class, () -> DomainLexicon.load(path));

        assertTrue(error.getMessage().contains("line 3"), error.getMessage());
        assertTrue(error.getMessage().contains("plumbing"), error.getMessage());
    }

    @Test
    void aFileWithoutAVersionLineIsRejected() throws Exception {
        Path path = writeLexicon(
                "Term,Tier,Note",
                "impl,technical,implementation suffix");

        IOException error = assertThrows(IOException.class, () -> DomainLexicon.load(path));

        assertTrue(error.getMessage().contains("Version"), error.getMessage());
    }

    @Test
    void aMissingFileIsRejectedWithItsPath() {
        Path path = tempDir.resolve("absent.csv");

        IOException error = assertThrows(IOException.class, () -> DomainLexicon.load(path));

        assertTrue(error.getMessage().contains("absent.csv"), error.getMessage());
        assertTrue(error.getMessage().contains("does not exist"), error.getMessage());
    }
}
