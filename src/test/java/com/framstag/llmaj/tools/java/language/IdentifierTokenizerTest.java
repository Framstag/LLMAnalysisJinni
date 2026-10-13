package com.framstag.llmaj.tools.java.language;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentifierTokenizerTest {

    @Test
    void camelCaseUnderscoreAndDigitsSplitTheSameName() {
        List<String> camelCase = IdentifierTokenizer.tokenize("ProjectBuilder");
        List<String> underscores = IdentifierTokenizer.tokenize("project_builder");
        List<String> withDigit = IdentifierTokenizer.tokenize("projectBuilder2");

        assertEquals(List.of("project", "builder"), camelCase);
        assertEquals(camelCase, underscores);
        assertEquals(camelCase, withDigit);
    }

    @Test
    void keywordsAndSingleCharactersAreDropped() {
        assertEquals(List.of("name"), IdentifierTokenizer.tokenize("publicName"));
        assertEquals(List.of("token"), IdentifierTokenizer.tokenize("aToken"));
        assertEquals(List.of(), IdentifierTokenizer.tokenize("a"));
    }

    @Test
    void keywordTokensAreDroppedWhereverTheyOccur() {
        assertEquals(List.of("realm", "cache"), IdentifierTokenizer.tokenize("newRealmClassCache"));
    }

    @Test
    void anAcronymStaysOneWord() {
        assertEquals(List.of("http", "client"), IdentifierTokenizer.tokenize("HTTPClient"));
        assertEquals(List.of("xml", "http", "request"), IdentifierTokenizer.tokenize("XMLHttpRequest"));
    }

    @Test
    void digitRunsCarryNoToken() {
        assertEquals(List.of("sha"), IdentifierTokenizer.tokenize("sha256"));
        assertEquals(List.of("utf", "encoder"), IdentifierTokenizer.tokenize("UTF8Encoder"));
    }

    @Test
    void occurrencesArePreserved() {
        assertEquals(List.of("project", "project"), IdentifierTokenizer.tokenize("ProjectProject"));
    }

    @Test
    void proseIsTokenizedLikeAnIdentifier() {
        assertEquals(List.of("the", "realm", "cache", "is", "empty"),
                IdentifierTokenizer.tokenize("The realm cache_is empty."));
    }

    @Test
    void emptyAndAbsentTextYieldNoToken() {
        assertEquals(List.of(), IdentifierTokenizer.tokenize(""));
        assertEquals(List.of(), IdentifierTokenizer.tokenize(null));
        assertEquals(List.of(), IdentifierTokenizer.tokenize("__"));
    }
}
