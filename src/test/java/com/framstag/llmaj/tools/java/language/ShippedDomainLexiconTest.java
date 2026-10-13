package com.framstag.llmaj.tools.java.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lexicon that ships with the analysis. It is data, so it is verified like data: every tier of the
 * design table has to be reachable through it, and the file has to be loadable, which also proves that it
 * carries no duplicate term.
 */
class ShippedDomainLexiconTest {

    private static final Path LEXICON = Path.of("analysis/software-architecture/facts/domain_lexicon.csv");

    @TempDir
    Path tempDir;

    private DomainLexicon loadShippedLexicon() throws IOException {
        assertTrue(Files.isRegularFile(LEXICON), "the analysis ships a lexicon at " + LEXICON);

        return DomainLexicon.load(LEXICON);
    }

    @Test
    void theShippedLexiconIsLoadableAndCarriesTheSeededVersion() throws IOException {
        DomainLexicon lexicon = loadShippedLexicon();

        assertEquals("1", lexicon.getVersion());
        assertTrue(lexicon.size() > 100, "the lexicon classifies more than a hundred terms");
    }

    @Test
    void theSeededExamplesCarryTheTierOfTheDesignTable() throws IOException {
        DomainLexicon lexicon = loadShippedLexicon();

        for (String term : new String[]{"impl", "manager", "factory", "builder", "handler", "util", "adapter",
                "abstract", "default", "dto", "bean", "annotation", "type"}) {
            assertEquals(LexiconTier.TECHNICAL, lexicon.tierOf(term), term + " is plumbing vocabulary");
        }

        for (String term : new String[]{"result", "value", "data", "item", "entry", "node", "list", "map",
                "key", "size", "name", "id", "context", "config", "configuration", "property"}) {
            assertEquals(LexiconTier.GENERIC, lexicon.tierOf(term), term + " carries shape rather than meaning");
        }

        for (String term : new String[]{"get", "set", "is", "has", "to", "from", "of", "with", "args", "tmp",
                "the", "and", "that"}) {
            assertEquals(LexiconTier.NOISE, lexicon.tierOf(term), term + " is boilerplate");
        }
    }

    @Test
    void aDomainWordOfAProjectStaysACandidateBecauseTheLexiconCannotKnowIt() throws IOException {
        DomainLexicon lexicon = loadShippedLexicon();

        for (String term : new String[]{"realm", "artifact", "resolution", "mojo", "invoice", "settlement"}) {
            assertEquals(LexiconTier.UNCLASSIFIED, lexicon.tierOf(term),
                    term + " is not plumbing and stays a candidate domain term");
        }
    }

    @Test
    void aTierListedTwiceIsRejectedInsteadOfSilentlyOverriding() throws IOException {
        Path path = tempDir.resolve("domain_lexicon.csv");
        Files.write(path, java.util.List.of(
                "Version,1",
                "Term,Tier,Note",
                "realm,technical,first judgement",
                "realm,generic,second judgement"));

        IOException error = assertThrows(IOException.class, () -> DomainLexicon.load(path));

        assertTrue(error.getMessage().contains("already listed"), error.getMessage());
    }

    @Test
    void aTermListedTwiceWithTheSameTierIsNotADefect() throws IOException {
        Path path = tempDir.resolve("domain_lexicon.csv");
        Files.write(path, java.util.List.of(
                "Version,1",
                "Term,Tier,Note",
                "realm,technical,first note",
                "realm,technical,second note"));

        assertEquals(LexiconTier.TECHNICAL, DomainLexicon.load(path).tierOf("realm"));
    }

    @Test
    void everySeededTierIsReachableThroughTheFile() throws IOException {
        DomainLexicon lexicon = loadShippedLexicon();

        assertNotEquals(LexiconTier.UNCLASSIFIED, lexicon.tierOf("factory"));
        assertNotEquals(LexiconTier.UNCLASSIFIED, lexicon.tierOf("value"));
        assertNotEquals(LexiconTier.UNCLASSIFIED, lexicon.tierOf("the"));
    }
}
