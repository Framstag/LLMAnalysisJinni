package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.serialization.DefaultNodeReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the response schema of the modernisation strategy analysis: the shape of the project level
 * entity list that holds the modernisation areas.
 * <p>
 * The schema carries the two promises of the design that a reader can check on the result alone: the
 * patterns of an area come from the catalogue of the marker facts, so a pattern outside the catalogue
 * is not a pattern the analysis may name, and the cost of an area rests on counted figures that are
 * named beside it, so the reader can weigh the cost in a way of their own.
 */
class ModernisationStrategySchemaTest {

    private static final Path SCHEMA_FILE =
            Path.of("analysis", "software-architecture", "results", "ModernisationStrategy.json");

    private static final List<String> ANCHOR_TYPES = List.of(
            "entry-point",
            "seam",
            "domain",
            "build-unit",
            "cut-point",
            "test-coverage",
            "technology-marker");

    private static final List<String> CATALOGUE_PATTERNS = List.of(
            "strangler-fig",
            "branch-by-abstraction",
            "module-extraction",
            "anticorruption-layer",
            "horizontal-lift",
            "retirement");

    @Test
    void theStrategySchemaDeclaresEveryPropertyItRequires() throws IOException {
        JsonNode schema = readSchema();

        assertEquals("ModernisationStrategy", schema.path("title").asText());
        assertEquals("object", schema.path("type").asText());
        assertEquals(List.of("reasoning", "areas", "absentTypes"), requiredFields(schema));

        JsonNode area = schema.path("properties").path("areas").path("items");
        assertEquals(List.of("module", "element", "anchorType", "patterns", "costFigures", "cost", "reasoning"),
                requiredFields(area));

        JsonNode pattern = area.path("properties").path("patterns").path("items");
        assertEquals(List.of("name", "evidenceFor", "evidenceAgainst", "whatWouldMakeItWrong"),
                requiredFields(pattern));

        JsonNode figure = area.path("properties").path("costFigures").path("items");
        assertEquals(List.of("name", "value", "source"), requiredFields(figure));

        assertEquals(List.of("type", "reason"),
                requiredFields(schema.path("properties").path("absentTypes").path("items")));

        assertDeclared(schema, "areas");
        assertDeclared(schema.path("properties").path("areas").path("items"), "patterns", "costFigures");
        assertDeclared(schema.path("properties").path("areas").path("items")
                .path("properties").path("patterns").path("items"), "name", "evidenceFor", "evidenceAgainst");
        assertDeclared(schema.path("properties").path("areas").path("items")
                .path("properties").path("costFigures").path("items"), "name", "value", "source");
        assertDeclared(schema.path("properties").path("absentTypes").path("items"), "type", "reason");
    }

    @Test
    void theStrategySchemaBoundsTheAnchorTypesAndThePatternCatalogue() throws IOException {
        JsonNode schema = readSchema();
        JsonNode area = schema.path("properties").path("areas").path("items");

        assertEquals(ANCHOR_TYPES, stringList(area.path("properties").path("anchorType").path("enum")));
        assertEquals(CATALOGUE_PATTERNS, stringList(area.path("properties")
                .path("patterns")
                .path("items")
                .path("properties")
                .path("name")
                .path("enum")));
        assertEquals("array", area.path("properties").path("patterns").path("type").asText());
        assertEquals("array", area.path("properties").path("costFigures").path("type").asText());
        JsonNode pattern = area.path("properties").path("patterns").path("items");
        assertEquals("array", pattern.path("properties").path("evidenceFor").path("type").asText());
        assertEquals("array", pattern.path("properties").path("evidenceAgainst").path("type").asText());
        assertEquals("string", pattern.path("properties").path("whatWouldMakeItWrong").path("type").asText());
    }

    @Test
    void aRenderedSampleAnswerValidatesAgainstTheStrategySchema() throws IOException {
        String answer = """
                {
                  "reasoning": "The areas follow from the anchors, the hotspots and the counted evidence. The order follows the counted cost and the choice of the area to work on is the reader's.",
                  "areas": [
                    {
                      "module": "core",
                      "element": "com.example.core.OrderPort",
                      "anchorType": "seam",
                      "patterns": [
                        {
                          "name": "branch-by-abstraction",
                          "evidenceFor": [
                            "The seam analysis counted 3 implementors of the interface.",
                            "The test coverage analysis counted 74 percent of the methods of the module covered."
                          ],
                          "evidenceAgainst": [
                            "The inter module dependency analysis counted 12 modules that import the interface."
                          ],
                          "whatWouldMakeItWrong": "No abstraction can be named that the callers would use."
                        }
                      ],
                      "costFigures": [
                        {
                          "name": "afferent coupling of the module",
                          "value": "12 incoming references",
                          "source": "The inter module dependency analysis counted the references of the module."
                        },
                        {
                          "name": "test coverage of the area",
                          "value": "74 percent",
                          "source": "The test coverage analysis counted the covered methods of the module."
                        }
                      ],
                      "cost": "A cheap start, because the tests around the interface are there, but the 12 incoming references have to follow the interface.",
                      "reasoning": "The interface is the widest abstraction of the module and the tests around it are there."
                    },
                    {
                      "module": "batch",
                      "element": "",
                      "anchorType": "build-unit",
                      "patterns": [
                        {
                          "name": "retirement",
                          "evidenceFor": [
                            "The import diversity analysis counted no caller of the batch module outside the module itself."
                          ],
                          "evidenceAgainst": [],
                          "whatWouldMakeItWrong": "The data of the batch has to be kept for audit reasons."
                        }
                      ],
                      "costFigures": [
                        {
                          "name": "number of files of the module",
                          "value": "4 files",
                          "source": "The file statistics analysis counted the files of the module."
                        }
                      ],
                      "cost": "A small change, four files and no caller left.",
                      "reasoning": "The build unit has no caller left, so it can be taken out of the system."
                    }
                  ],
                  "absentTypes": [
                    {
                      "type": "hotspot",
                      "reason": "The hotspot analysis did not run, so the areas that rest on the hotspots are absent."
                    }
                  ]
                }
                """;

        List<String> violations = violations(answer);

        assertTrue(violations.isEmpty(), "the sample answer must validate, got: " + violations);
    }

    @Test
    void anAreaWithAPatternOutsideTheCatalogueDoesNotValidate() throws IOException {
        String answer = """
                {
                  "reasoning": "The areas follow from the anchors and the counted evidence.",
                  "areas": [
                    {
                      "module": "core",
                      "element": "com.example.core.OrderPort",
                      "anchorType": "seam",
                      "patterns": [
                        {
                          "name": "rewrite-in-place",
                          "evidenceFor": [
                            "The seam analysis counted 3 implementors of the interface."
                          ],
                          "evidenceAgainst": [],
                          "whatWouldMakeItWrong": "The interface cannot be named by the callers."
                        }
                      ],
                      "costFigures": [],
                      "cost": "An unknown cost.",
                      "reasoning": "The interface is the widest abstraction of the module."
                    }
                  ],
                  "absentTypes": []
                }
                """;

        List<Error> errors = errors(answer);

        assertFalse(errors.isEmpty(),
                "a pattern outside the catalogue must not validate against the schema");
        assertTrue(errors.stream().anyMatch(error -> error.getInstanceLocation().toString().contains("patterns")),
                "the violation must point at the pattern of the area, got: "
                        + errors.stream().map(Error::getInstanceLocation).toList());
        assertTrue(errors.stream().anyMatch(error -> error.getMessage().contains("strangler-fig")),
                "the violation must name the catalogue the pattern is bound to, got: "
                        + errors.stream().map(Error::getMessage).toList());
    }

    @Test
    void theStrategySchemaIsAValidJsonSchema() throws IOException {
        SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(
                Dialects.getDraft202012(),
                builder -> builder.nodeReader(DefaultNodeReader.Builder::locationAware));
        Schema metaSchema = schemaRegistry.getSchema(SchemaLocation.of(Dialects.getDraft202012().getId()));

        List<String> errors = metaSchema
                .validate(Files.readString(SCHEMA_FILE), InputFormat.JSON, executionContext ->
                        executionContext.executionConfig(config -> config.formatAssertionsEnabled(true)))
                .stream()
                .map(com.networknt.schema.Error::getMessage)
                .toList();

        assertTrue(errors.isEmpty(), "the response schema must be a valid schema, got: " + errors);
    }

    private static void assertDeclared(JsonNode schema, String... properties) {
        JsonNode declared = schema.path("properties");

        for (String property : properties) {
            assertTrue(declared.has(property),
                    "the schema must declare the property it requires: " + property);
        }
    }

    private static JsonNode readSchema() throws IOException {
        return new ObjectMapper().readTree(SCHEMA_FILE.toFile());
    }

    private static List<String> violations(String answer) throws IOException {
        return errors(answer).stream()
                .map(Error::getMessage)
                .toList();
    }

    private static List<Error> errors(String answer) throws IOException {
        SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(
                Dialects.getDraft202012(),
                builder -> builder.nodeReader(DefaultNodeReader.Builder::locationAware));
        Schema validator = schemaRegistry.getSchema(readSchema().toString(), InputFormat.JSON);

        return validator.validate(answer, InputFormat.JSON);
    }

    private static List<String> requiredFields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.path("required").forEach(field -> fields.add(field.asText()));

        return fields;
    }

    private static List<String> stringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));

        return values;
    }
}
