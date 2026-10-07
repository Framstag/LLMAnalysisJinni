package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.serialization.DefaultNodeReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the response schemas of the analysis domains as they are shipped. A schema that requires a
 * property it does not declare can never be satisfied: the description rendered into the prompt is
 * built from {@code properties}, so the model is never told about that property, and every attempt is
 * rejected. `results/TechnologyStack.json` was such a schema, which is why the technology stack task
 * of the Maven run failed in all three attempts.
 */
public class AnalysisResponseSchemaTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Path DOMAIN_RESULTS =
            Path.of("analysis", "software-architecture", "results");

    @Test
    public void testEveryRequiredPropertyIsDeclared() throws IOException {
        List<Path> schemaFiles = responseSchemas();
        List<String> findings = new ArrayList<>();

        assertFalse(schemaFiles.isEmpty(), "the analysis domain must ship response schemas");

        for (Path schemaFile : schemaFiles) {
            collectFindings(schemaFile, mapper.readTree(schemaFile.toFile()), "", findings);
        }

        assertTrue(findings.isEmpty(),
                "no response schema may require a property it does not declare, found: " + findings);
    }

    @Test
    public void testTheTechnologyStackSchemaAcceptsTheAnswerOfTheRun() throws IOException {
        JsonNode schema = mapper.readTree(DOMAIN_RESULTS.resolve("TechnologyStack.json").toFile());

        List<String> required = new ArrayList<>();
        schema.path("required").forEach(name -> required.add(name.asText()));

        assertFalse(required.contains("architecture"),
                "the schema must not require a property its prompt cannot describe, found: " + required);

        String answer = """
                {
                  "reasoning": "The SBOM could not be loaded, so no dependency data is available.",
                  "summary": "The stack cannot be matched to a concrete reference stack without dependencies.",
                  "technologies": [],
                  "derivations": [
                    {
                      "aspect": "Data availability",
                      "derivation": "No dependencies could be evaluated, so no technology stack was derived."
                    }
                  ]
                }
                """;

        List<String> violations = violations(schema.toString(), answer);

        assertTrue(violations.isEmpty(),
                "an answer with an empty result and a reason must be valid, got: " + violations);
    }

    /**
     * The chain of a project without an SBOM: the load task reports the absence, and the two tasks that
     * need dependency data answer with an explicitly empty result and a reason. The Maven run of
     * 2026-10-06 produced exactly these answers, and the technology stack one was rejected for a schema
     * defect that had nothing to do with the missing SBOM.
     */
    @Test
    public void testSbmLessAnswersValidate() throws IOException {
        String loadAnswer = """
                {
                  "result": "ERROR",
                  "reason": "The project contains no SBOM, so no dependency data is available."
                }
                """;

        assertTrue(violations(schema("Result.json"), loadAnswer).isEmpty(),
                "the load task must be able to report the absence: "
                        + violations(schema("Result.json"), loadAnswer));

        String licenseAnswer = """
                {
                  "licenses": [],
                  "reasoning": "No SBOM was loaded, so no licenses could be identified.",
                  "evaluation": "No license evaluation is possible without dependency data.",
                  "compliance": "Not assessable.",
                  "actionItems": []
                }
                """;

        assertTrue(violations(schema("LicenseEvaluation.json"), licenseAnswer).isEmpty(),
                "the license evaluation must be able to answer with an empty list: "
                        + violations(schema("LicenseEvaluation.json"), licenseAnswer));
    }

    private static String schema(String fileName) throws IOException {
        return mapper.readTree(DOMAIN_RESULTS.resolve(fileName).toFile()).toString();
    }

    /**
     * Validates a payload the way the engine does, so a schema is judged by the validator that decides
     * about a run.
     */
    private static List<String> violations(String schema, String payload) {
        SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(
                Dialects.getDraft202012(),
                builder -> builder.nodeReader(DefaultNodeReader.Builder::locationAware));
        Schema validator = schemaRegistry.getSchema(schema, InputFormat.JSON);

        return validator.validate(payload, InputFormat.JSON).stream()
                .map(com.networknt.schema.Error::getMessage)
                .toList();
    }

    private static List<Path> responseSchemas() throws IOException {
        Path analysisRoot = Path.of("analysis");

        try (Stream<Path> domains = Files.list(analysisRoot)) {
            return domains.filter(Files::isDirectory)
                    .flatMap(domain -> resultsOf(domain))
                    .sorted()
                    .toList();
        }
    }

    private static Stream<Path> resultsOf(Path domain) {
        Path results = domain.resolve("results");

        if (!Files.isDirectory(results)) {
            return Stream.empty();
        }

        try {
            return Files.list(results)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList()
                    .stream();
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    /**
     * Walks a schema and records every required name that the same schema does not declare, including
     * the nested object schemas of properties and array items.
     */
    private static void collectFindings(Path schemaFile, JsonNode schema, String path, List<String> findings) {
        if (!schema.isObject()) {
            return;
        }

        JsonNode properties = schema.path("properties");

        if (properties.isObject()) {
            for (JsonNode requiredName : schema.path("required")) {
                if (!properties.has(requiredName.asText())) {
                    findings.add(schemaFile.getFileName() + path + " requires undeclared property '"
                            + requiredName.asText() + "'");
                }
            }

            properties.fields().forEachRemaining(entry ->
                    collectFindings(schemaFile, entry.getValue(), path + "/" + entry.getKey(), findings));
        }

        JsonNode items = schema.path("items");

        if (items.isObject()) {
            collectFindings(schemaFile, items, path + "[]", findings);
        }
    }
}
