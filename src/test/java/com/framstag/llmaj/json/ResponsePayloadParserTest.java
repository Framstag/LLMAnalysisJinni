package com.framstag.llmaj.json;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the location and parsing of a model response payload.
 * <p>
 * The fixtures follow the shapes of responses recorded in
 * {@code workspaces/spring-petclinic/logs/BuildSystems.log} and {@code LicenseEvaluation.log}: a
 * sentence of prose, then a fenced JSON document.
 */
public class ResponsePayloadParserTest {

    private final ResponsePayloadParser parser =
            new ResponsePayloadParser(ObjectMapperFactory.getJSONObjectMapperInstance());

    /**
     * A response in which the model explains itself before the fenced answer, the shape that used to
     * fail with "Unrecognized token 'I'".
     */
    private static final String BUILD_SYSTEMS_FIXTURE = """
            I found the following files in the project root: `settings.gradle`, `build.gradle`, and `pom.xml`.\
             This indicates the presence of both Gradle (Classic variant) and Maven.

            ```json
            {
            "reasoning": "I searched the project root recursively for files matching the build system wildcards 'pom.xml', '*.gradle', and '*.gradle.kts'. The search returned 'settings.gradle', 'build.gradle', and 'pom.xml', all located in the project root directory.",
            "buildsystems": [
            {
            "reasoning": "The files 'build.gradle' and 'settings.gradle' were found in the project root directory.",
            "name": "Gradle",
            "variant": "Classic"
            },
            {
            "reasoning": "The file 'pom.xml' was found in the project root directory.",
            "name": "Maven",
            "variant": ""
            }
            ]
            }
            ```
            """;

    /**
     * The other recorded shape: prose, then a fenced document with nested arrays and punctuation in
     * the strings.
     */
    private static final String LICENSE_FIXTURE = """
            I now have all the information needed. Let me compile the comprehensive license analysis.

            ```json
            {
            "licenses": [
            {
            "name": "Apache-2.0",
            "compatibility": "Compatible with most permissive and weak-copyleft licenses (MIT, BSD, EPL, MPL, LGPL). One-way compatible into GPLv3, but not into GPLv2."
            },
            {
            "name": "MIT",
            "compatibility": "Compatible with nearly all licenses, including Apache-2.0, BSD, EPL, MPL, LGPL and GPL."
            }
            ],
            "actionItems": [
            {
            "aspect": "License combination / compatibility",
            "criticality": "MEDIUM"
            }
            ]
            }
            ```
            """;

    @Test
    public void parsesPayloadThatIsTheWholeResponse() throws Exception {
        JsonNode parsed = parser.parse("{\"buildsystems\": []}");

        assertTrue(parsed.has("buildsystems"), "a bare payload must be parsed as it is");
    }

    @Test
    public void parsesPayloadInALeadingFence() throws Exception {
        JsonNode parsed = parser.parse("```json\n{\"buildsystems\": []}\n```");

        assertTrue(parsed.has("buildsystems"), "a payload in a leading fence must be located");
    }

    @Test
    public void parsesPayloadInAFenceAfterProse() throws Exception {
        JsonNode parsed = parser.parse(BUILD_SYSTEMS_FIXTURE);

        assertTrue(parsed.has("buildsystems"), "the payload behind the prose must be located, got: " + parsed);
        assertEquals(2, parsed.get("buildsystems").size(), "the whole payload must be sliced");
        assertEquals("Maven", parsed.get("buildsystems").get(1).get("name").asText());
    }

    @Test
    public void parsesNestedArraysAndPunctuationBehindProse() throws Exception {
        JsonNode parsed = parser.parse(LICENSE_FIXTURE);

        assertEquals(2, parsed.get("licenses").size(), "the whole payload must be sliced, got: " + parsed);
        assertEquals(1, parsed.get("actionItems").size());
        assertTrue(parsed.get("licenses").get(0).get("compatibility").asText().contains("(MIT, BSD, EPL"),
                "punctuation inside a string must survive the slice");
    }

    @Test
    public void parsesPayloadInAFenceBeforeTrailingProse() throws Exception {
        JsonNode parsed = parser.parse("""
                ```json
                {"buildsystems": []}
                ```

                I hope this helps.
                """);

        assertTrue(parsed.has("buildsystems"), "text after the payload must not end up in it");
    }

    @Test
    public void prefersThePayloadOverAnEmptyFragmentInProse() throws Exception {
        JsonNode parsed = parser.parse("""
                The response schema is `{}` when nothing is required.

                ```json
                {"buildsystems": [{"name": "Maven"}]}
                ```
                """);

        assertEquals(1, parsed.get("buildsystems").size(),
                "the empty fragment mentioned in the prose must not win, got: " + parsed);
    }

    @Test
    public void parsesPayloadWithBracketsAndEscapedQuotesInsideStrings() throws Exception {
        String response = "Here it is: "
                + "{\"reasoning\": \"he said \\\"hello\\\" to {her} about [this]\", \"items\": [1, 2]}";

        JsonNode parsed = parser.parse(response);

        assertEquals(2, parsed.get("items").size(),
                "a bracket inside a string must not end the payload, got: " + parsed);
        assertTrue(parsed.get("reasoning").asText().contains("\"hello\""),
                "an escaped quote must survive the slice");
    }

    @Test
    public void parsesSingleLinePayloadWithSurroundingText() throws Exception {
        JsonNode parsed = parser.parse("Answer: {\"buildsystems\": []} — done.");

        assertTrue(parsed.has("buildsystems"), "a single line payload must be located");
    }

    @Test
    public void locatedPayloadThatParsesIsReturnedUnchanged() {
        String payload = "{\"reasoning\": \"he said \\\"hello\\\" about {this} and [that], twice;\", "
                + "\"items\": [1, 2]}";
        String response = "Here it is: " + payload + " - I hope this helps.";

        assertEquals(payload, parser.payloadText(response),
                "a payload that parses as it is must be returned unchanged, without repair");
    }

    @Test
    public void reportsAResponseWithoutAnyPayload() {
        String response = "y".repeat(5000) + "TAIL";

        ResponsePayloadException exception =
                assertThrows(ResponsePayloadException.class, () -> parser.parse(response));

        assertEquals(ResponsePayloadException.Condition.NO_PAYLOAD_LOCATED, exception.getCondition(),
                "prose without brackets has no payload");
        assertTrue(exception.getMessage().contains("No JSON payload found in the response"),
                "the report must name the missing payload, got: " + exception.getMessage());
        assertEquals(response.length(), exception.getResponseLength(),
                "the report must say how long the response was");
        assertTrue(exception.getExcerpt().length() <= ResponsePayloadParser.EXCERPT_LENGTH,
                "the excerpt must be bounded, got " + exception.getExcerpt().length() + " characters");
        assertTrue(exception.getMessage().length() < 1000,
                "the report must stay bounded, got " + exception.getMessage().length() + " characters");
    }

    @Test
    public void reportsATruncatedPayload() {
        String response = "Here is the analysis:\n```json\n{\"buildsystems\": [{\"name\": \"Maven\"";

        ResponsePayloadException exception =
                assertThrows(ResponsePayloadException.class, () -> parser.parse(response));

        assertEquals(ResponsePayloadException.Condition.NO_PAYLOAD_LOCATED, exception.getCondition(),
                "a payload without its closing bracket is not a payload");
        assertTrue(exception.getMessage().contains("No JSON payload found in the response"),
                "a truncated payload must be reported as missing, got: " + exception.getMessage());
    }

    @Test
    public void reportsALocatedPayloadThatDoesNotParse() {
        String response = "Here it is:\n```json\n{\"buildsystems\": [{\"name\": \"Maven\" \"variant\": \"\"}]}\n```";

        ResponsePayloadException exception =
                assertThrows(ResponsePayloadException.class, () -> parser.parse(response));

        assertEquals(ResponsePayloadException.Condition.PAYLOAD_NOT_PARSEABLE, exception.getCondition(),
                "a located but broken payload is a different condition");
        assertTrue(exception.getMessage().contains("JSON payload found in the response is not parseable"),
                "the report must say that the payload itself is the problem, got: " + exception.getMessage());
    }

    @Test
    public void doesNotUseAFragmentOfABrokenPayloadAsTheResult() {
        // Missing comma between the two array elements: the outer document is broken, but the first
        // element on its own would parse. That fragment is part of the payload, not the payload.
        String response = "Here it is:\n```json\n{\"buildsystems\": [{\"name\": \"Maven\"} {\"name\": \"Gradle\"}]}\n```";

        ResponsePayloadException exception =
                assertThrows(ResponsePayloadException.class, () -> parser.parse(response));

        assertEquals(ResponsePayloadException.Condition.PAYLOAD_NOT_PARSEABLE, exception.getCondition(),
                "the run must report the broken payload, not quietly keep a fragment of it");
    }

    @Test
    public void repairsUnescapedQuotesInALocatedPayload() throws Exception {
        JsonNode parsed = parser.parse("""
                ```json
                {"reasoning": "the tool returned "no such file" for the path"}
                ```
                """);

        assertEquals("the tool returned \"no such file\" for the path", parsed.get("reasoning").asText(),
                "the unescaped quotes must be repaired");
    }

    @Test
    public void acceptsAnEmptyDocumentWhenItIsAllTheResponseCarries() throws Exception {
        JsonNode parsed = parser.parse("```json\n{}\n```");

        assertTrue(parsed.isObject() && parsed.isEmpty(),
                "an empty document is a valid answer when nothing else is in the response");
    }

    @Test
    public void missingPayloadIsReportedAsAnIoException() {
        ResponsePayloadException exception =
                assertThrows(ResponsePayloadException.class, () -> parser.parse("no payload here"));

        assertInstanceOf(IOException.class, exception,
                "the task runner catches IOException and Exception alike, so the failure keeps reaching"
                        + " markTaskAsFailed()");
    }

    @Test
    public void doesNotLogTheWholeResponseBody() {
        ListAppender<ILoggingEvent> records = new ListAppender<>();
        records.start();
        Logger parserLogger = (Logger) LoggerFactory.getLogger(ResponsePayloadParser.class);
        parserLogger.addAppender(records);

        try {
            String response = "y".repeat(5000) + "TAIL";

            assertThrows(ResponsePayloadException.class, () -> parser.parse(response));

            assertFalse(records.list.isEmpty(), "the failure must be reported in the log");

            List<String> messages = records.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertFalse(messages.isEmpty(), "the failure condition must be logged at WARN level");

            for (String message : messages) {
                assertFalse(message.contains("TAIL"),
                        "the log must not carry the whole response body, got: " + message.length()
                                + " characters");
                assertTrue(message.length() < 1000,
                        "the logged report must stay bounded, got " + message.length() + " characters");
            }
        } finally {
            parserLogger.detachAppender(records);
            records.stop();
        }
    }
}
