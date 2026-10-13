package com.framstag.llmaj.tools.java.language;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge between the raw text of a report and the tokenizer. Both cases here were found on a real project
 * corpus: a class file writes {@code <init>} where the source wrote a constructor, and it writes
 * {@code Ljava/lang/String;} where the source wrote {@code java.lang.String}.
 */
class ProseTextTest {

    @Test
    void aClassFileDescriptorIsReducedToTheNameItCarries() {
        assertEquals("String", ProseText.simpleTypeName("Ljava/lang/String;"));
        assertEquals("Realm", ProseText.simpleTypeName("Lcom/example/core/Realm;"));
        assertEquals("List", ProseText.simpleTypeName("java.util.List"));
        assertEquals("List", ProseText.simpleTypeName("List<String>"));
        assertEquals("Entry", ProseText.simpleTypeName("Ljava/util/Map$Entry;"));
        // A primitive descriptor is a single letter; it carries no token of its own.
        assertEquals("I", ProseText.simpleTypeName("I"));
    }

    @Test
    void anArrayDescriptorIsReducedToItsElementName() {
        assertEquals("String", ProseText.simpleTypeName("[Ljava/lang/String;"));

        // A primitive descriptor letter is left as it is; a single character carries no token of its own.
        assertEquals("B", ProseText.simpleTypeName("[B"));
        assertEquals(List.of(), IdentifierTokenizer.tokenize(ProseText.simpleTypeName("[B")));
    }

    @Test
    void aDescriptorNeverLeaksAPackageNameIntoTheVocabulary() {
        List<String> tokens = IdentifierTokenizer.tokenize(ProseText.simpleTypeName("Ljava/lang/String;"));

        assertEquals(List.of("string"), tokens);
        assertFalse(tokens.contains("java"));
        assertFalse(tokens.contains("lang"));
    }

    @Test
    void theSyntheticNamesOfAClassFileAreNotNames() {
        assertFalse(ProseText.isDeclaredName("<init>"));
        assertFalse(ProseText.isDeclaredName("<clinit>"));
        assertFalse(ProseText.isDeclaredName("  "));
        assertFalse(ProseText.isDeclaredName(null));
        assertTrue(ProseText.isDeclaredName("resolveRealm"));
    }

    @Test
    void markupCarriesNoDomain() {
        List<String> tokens = ProseText.tokens("""
                <p>Returns the {@code RealmCache} of the realm.
                @param realm the realm to use
                @since 3.9
                <li>first item
                <td>second cell
                """);

        assertTrue(tokens.contains("realm"));
        assertTrue(tokens.contains("cache"));
        assertFalse(tokens.contains("code"), "the inline tag name is markup, not vocabulary");
        assertFalse(tokens.contains("li"), "an HTML element name is markup");
        assertFalse(tokens.contains("td"), "an HTML element name is markup");
        assertFalse(tokens.contains("since"), "a block tag name is markup");
    }

    @Test
    void anAuthorNameCarriesNoDomain() {
        List<String> tokens = ProseText.tokens("""
                 * The realm cache of the project.
                 * @author Benjamin Bentmann
                 * @see RealmCache
                """);

        assertTrue(tokens.contains("realm"));
        assertFalse(tokens.contains("benjamin"), "the name of an author is not vocabulary of the module");
        assertFalse(tokens.contains("bentmann"));
    }

    @Test
    void aSentenceThatMentionsAnAuthorKeepsItsWords() {
        List<String> tokens = ProseText.tokens("The author of the realm cache is a contributor");

        assertTrue(tokens.contains("author"), "only the javadoc tag line is dropped, not the English word");
        assertTrue(tokens.contains("realm"));
    }

    @Test
    void absentAndEmptyProseYieldsNoToken() {
        assertEquals(List.of(), ProseText.tokens(null));
        assertEquals(List.of(), ProseText.tokens(""));
        assertEquals(List.of(), ProseText.tokens("<p> <li>"));
    }
}
