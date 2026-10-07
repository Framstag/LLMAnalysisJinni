package com.framstag.llmaj.lc4j;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.serialization.DefaultNodeReader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the report of a schema violation. The Maven run of 2026-10-06 recorded 188 violations of the
 * batch metric answers as the validator's message alone - in German, without the position inside the
 * payload and without the rejected value - so neither a reader nor the next attempt could tell what to
 * change.
 */
public class SchemaViolationReportTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "reasoning": {"type": "string"},
                "moduleEvaluations": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "moduleName": {"type": "string"},
                      "evaluations": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "urgency": {"type": "string", "enum": ["NONE", "LOW", "MEDIUM", "HIGH"]},
                            "count": {"type": "integer"}
                          },
                          "required": ["urgency"]
                        }
                      }
                    },
                    "required": ["moduleName"]
                  }
                }
              },
              "required": ["reasoning", "moduleEvaluations"]
            }
            """;

    private static final String PAYLOAD = """
            {
              "moduleEvaluations": [
                {
                  "evaluations": [
                    {"urgency": "low", "count": "seven"}
                  ]
                }
              ]
            }
            """;

    private static List<String> reports(String payload) {
        SchemaRegistry registry = SchemaRegistry.withDialect(Dialects.getDraft202012(),
                builder -> builder.nodeReader(DefaultNodeReader.Builder::locationAware));
        Schema schema = registry.getSchema(SCHEMA, InputFormat.JSON);

        return SchemaViolationReport.of(schema.validate(payload, InputFormat.JSON));
    }

    private static String reportContaining(List<String> reports, String text) {
        return reports.stream()
                .filter(report -> report.contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no report contains '" + text + "', got: " + reports));
    }

    @Test
    public void testEnumViolationNamesLocationValueAndPermittedValues() {
        List<String> reports = reports(PAYLOAD);

        String report = reportContaining(reports, "not one of the permitted values");

        assertTrue(report.contains("/moduleEvaluations/0/evaluations/0/urgency"),
                "the report must name the position of the rejected value, got: " + report);
        assertTrue(report.contains("\"low\""),
                "the report must name the rejected value, got: " + report);
        assertTrue(report.contains("[NONE, LOW, MEDIUM, HIGH]"),
                "the report must name the permitted values, got: " + report);
    }

    @Test
    public void testMissingRequiredPropertyNamesItsLocation() {
        List<String> reports = reports(PAYLOAD);

        String missingModule = reportContaining(reports, "'moduleName' is missing");

        assertTrue(missingModule.contains("/moduleEvaluations/0"),
                "the report must name the object that lacks the property, got: " + missingModule);

        String missingReasoning = reportContaining(reports, "'reasoning' is missing");

        assertEquals("the payload: the required property 'reasoning' is missing", missingReasoning,
                "a missing property of the payload itself must be reported against the payload");
    }

    @Test
    public void testTypeViolationNamesTheRejectedValueAndTheExpectedType() {
        List<String> reports = reports(PAYLOAD);

        String report = reportContaining(reports, "is not of type");

        assertTrue(report.contains("/moduleEvaluations/0/evaluations/0/count"),
                "the report must name the position of the rejected value, got: " + report);
        assertTrue(report.contains("\"seven\""),
                "the report must name the rejected value, got: " + report);
        assertTrue(report.contains("integer"),
                "the report must name the expected type, got: " + report);
    }

    @Test
    public void testReportDoesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();

        try {
            Locale.setDefault(Locale.GERMANY);
            List<String> german = reports(PAYLOAD);

            Locale.setDefault(Locale.US);
            List<String> english = reports(PAYLOAD);

            assertEquals(english, german,
                    "the same payload must yield the same report on every machine");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void testEveryViolationOfAPayloadIsReported() {
        StringBuilder payload = new StringBuilder("{\"reasoning\":\"\",\"moduleEvaluations\":[");

        for (int index = 0; index < 50; index++) {
            if (index > 0) {
                payload.append(',');
            }

            payload.append("{\"moduleName\":\"module").append(index)
                    .append("\",\"evaluations\":[{\"urgency\":\"low\"}]}");
        }

        payload.append("]}");

        List<String> reports = reports(payload.toString());

        assertEquals(50, reports.size(),
                "one report per wrong value, got: " + reports.size());
        assertTrue(reports.stream().allMatch(report -> report.contains("\"low\"")),
                "every report must name the rejected value");
    }

    @Test
    public void testAConformantPayloadProducesNoReport() {
        List<String> reports = reports("""
                {"reasoning":"a reason", "moduleEvaluations":[{"moduleName":"module"}]}
                """);

        assertTrue(reports.isEmpty(), "a conformant payload must not produce a report, got: " + reports);
    }

    @Test
    public void testALongRejectedValueIsShortened() {
        String payload = """
                {"reasoning":"a reason", "moduleEvaluations":[{"moduleName":"module",
                 "evaluations":[{"urgency":"%s"}]}]}
                """.formatted("L".repeat(400));

        String report = reportContaining(reports(payload), "not one of the permitted values");

        assertTrue(report.length() <= 200,
                "a long rejected value must be shortened, got " + report.length() + " characters");
        assertTrue(report.contains("..."), "the shortening must be visible, got: " + report);
    }
}
