package com.framstag.llmaj.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonHelperTest {

    @Test
    public void testExtractJSONIdentity() {
        String jsonString = "{}";

        String newJsonString = JsonHelper.extractJSON(jsonString);

        assertEquals(jsonString, newJsonString);

    }

    @Test
    public void testExtractJSONWrapped() {
        String jsonString = "```json{}```";

        String newJsonString = JsonHelper.extractJSON(jsonString);

        assertEquals("{}", newJsonString);
    }

    @Test
    public void testExtractJSONBadQuotes() {
        String jsonStringGood = """
                {
                  "attribute" : "bad \\"quoted\\" text"
                }""";

        String jsonStringBad = """
                {
                  "attribute" : "bad "quoted" text"
                }""";

        String newJsonString = JsonHelper.extractJSON(jsonStringBad);

        assertEquals(jsonStringGood, newJsonString);
    }
}
