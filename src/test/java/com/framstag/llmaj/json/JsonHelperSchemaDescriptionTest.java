package com.framstag.llmaj.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the description of a response schema that the engine renders into the prompt. The Maven run
 * of 2026-10-06 rejected every batch metric answer because the description stopped at an array whose
 * item schema has no title: the model never saw the nested properties, their required flags, or the
 * permitted values of an enumerated property, and guessed the spelling of those values.
 */
public class JsonHelperSchemaDescriptionTest {

    private static final ObjectMapper mapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    private static final Path DOMAIN_RESULTS = Path.of("analysis", "software-architecture", "results");

    private static JsonNode schema(String json) throws IOException {
        return mapper.readTree(json);
    }

    private static String descriptionOf(String json) throws IOException {
        return JsonHelper.createTypeDescription(schema(json));
    }

    @Test
    public void testUntitledArrayItemsAreDescribed() throws IOException {
        String description = descriptionOf("""
                {
                  "type": "object",
                  "properties": {
                    "reasoning": {"type": "string", "description": "Overall reasoning"},
                    "moduleEvaluations": {
                      "type": "array",
                      "description": "One evaluation entry per affected module",
                      "items": {
                        "type": "object",
                        "properties": {
                          "moduleName": {"type": "string"},
                          "urgency": {"type": "string", "enum": ["NONE", "LOW", "MEDIUM", "HIGH"]}
                        },
                        "required": ["moduleName", "urgency"]
                      }
                    }
                  },
                  "required": ["reasoning", "moduleEvaluations"]
                }
                """);

        assertTrue(description.contains("type: array of objects:"),
                "an item schema without a title must be described, got:\n" + description);
        assertTrue(description.contains("\"moduleName\""),
                "the properties of the item must be named, got:\n" + description);
        assertTrue(description.contains("allowed: [\"NONE\", \"LOW\", \"MEDIUM\", \"HIGH\"]"),
                "the permitted values of a nested enum must be named, got:\n" + description);
    }

    @Test
    public void testTitledArrayItemsKeepTheirName() throws IOException {
        String description = descriptionOf("""
                {
                  "type": "object",
                  "properties": {
                    "technologies": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "title": "Technology",
                        "properties": {"technology": {"type": "string"}},
                        "required": ["technology"]
                      }
                    }
                  }
                }
                """);

        assertTrue(description.contains("type: array of Technology:"),
                "a titled item keeps its name, got:\n" + description);
        assertTrue(description.contains("\"technology\""),
                "the properties of a titled item must be described too, got:\n" + description);
    }

    @Test
    public void testRequiredPropertiesAreNamed() throws IOException {
        String description = descriptionOf("""
                {
                  "type": "object",
                  "properties": {
                    "summary": {"type": "string"},
                    "detail": {"type": "string"}
                  },
                  "required": ["summary"]
                }
                """);

        assertTrue(description.contains("summary"),
                "the required property must be described, got:\n" + description);
        assertTrue(description.contains("; required)"),
                "the required status must be visible on the property, got:\n" + description);
        assertFalse(description.contains("detail\": (; required)"),
                "a property the schema does not require must not be marked, got:\n" + description);
    }

    @Test
    public void testTwoNestedLevelsAreDescribed() throws IOException {
        String description = descriptionOf("""
                {
                  "type": "object",
                  "properties": {
                    "modules": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "findings": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "properties": {"finding": {"type": "string"}}
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """);

        assertTrue(description.contains("\"findings\""),
                "the first nesting level must appear, got:\n" + description);
        assertTrue(description.contains("\"finding\""),
                "the second nesting level must appear, got:\n" + description);
    }

    @Test
    public void testDeepNestingIsSummarisedInsteadOfUnbounded() throws IOException {
        String description = descriptionOf(deepSchema(12).toPrettyString());

        assertTrue(description.contains("nested structure, see the JSON schema"),
                "a schema deeper than the bound must be summarised, got:\n" + description);
        assertTrue(description.length() < 4000,
                "the description of a deep schema must stay small, got " + description.length()
                        + " characters");
    }

    private static JsonNode deepSchema(int levels) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");

        ObjectNode current = root;

        for (int level = 0; level < levels; level++) {
            ObjectNode properties = current.putObject("properties");
            ObjectNode child = properties.putObject("level" + level);
            child.put("type", "object");
            current = child;
        }

        return root;
    }

    /**
     * The regression of the Maven run: the batch metric schema must reach the prompt with the item
     * properties and the values the model has to answer with.
     */
    @Test
    public void testBatchMetricSchemaIsDescribedCompletely() throws IOException {
        JsonNode schema = mapper.readTree(DOMAIN_RESULTS.resolve("ModuleBatchEvaluation.json").toFile());

        String description = JsonHelper.createTypeDescription(schema);

        for (String expected : new String[]{"moduleEvaluations", "moduleName", "evaluations", "aspect",
                "expectation", "reasoning", "finding", "recommendation", "urgency", "criticality"}) {
            assertTrue(description.contains(expected),
                    "the description must name '" + expected + "', got:\n" + description);
        }

        assertTrue(description.contains("allowed: [\"NONE\", \"LOW\", \"MEDIUM\", \"HIGH\"]"),
                "the description must name the permitted values of urgency and criticality, got:\n"
                        + description);
        assertFalse(description.contains("\"moduleEvaluations\": (One evaluation entry per affected module; type: array)"),
                "the array of module evaluations must not stay opaque, got:\n" + description);
    }

    @Test
    public void testEnumeratedSchemasOfTheDomainAreDescribedWithTheirValues() throws IOException {
        record EnumeratedSchema(String file, String... values) {
        }

        EnumeratedSchema[] schemas = {
                new EnumeratedSchema("ArchitectureEvaluation.json", "NONE", "LOW", "MEDIUM", "HIGH"),
                new EnumeratedSchema("LicenseEvaluation.json", "NONE", "LOW", "MEDIUM", "HIGH"),
                new EnumeratedSchema("ModuleArchitecture.json", "LOW", "MEDIUM", "HIGH"),
                new EnumeratedSchema("ModuleAnalysisReportsAll.json", "GENERATED", "REUSED", "SKIPPED", "ERROR"),
                new EnumeratedSchema("ModuleSubdirectories.json", "Src", "GenSrc", "TestSrc", "TestGenSrc", "Obj", "TestObj")
        };

        for (EnumeratedSchema enumerated : schemas) {
            String description = JsonHelper.createTypeDescription(
                    mapper.readTree(DOMAIN_RESULTS.resolve(enumerated.file()).toFile()));

            for (String value : enumerated.values()) {
                assertTrue(description.contains("\"" + value + "\""),
                        enumerated.file() + " must name the permitted value '" + value
                                + "', got:\n" + description);
            }
        }
    }
}
