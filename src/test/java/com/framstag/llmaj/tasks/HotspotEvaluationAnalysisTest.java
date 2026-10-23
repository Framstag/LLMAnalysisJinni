package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.serialization.DefaultNodeReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the hotspot evaluation: the response schema, the prompt and the task entry of the analysis
 * domain of the project.
 * <p>
 * The evaluation is the place where the counted hotspots are read and their limits are stated. The tests
 * hold it to the promises of the design: the answer names the two figures the join holds, the rank is a
 * rank among the classes of this project and not a threshold, the absence of the counted history is
 * reported as an absence and not as an empty ranking, and the prompt reads the counted hotspots of the
 * tool and no finding of another evaluation.
 */
class HotspotEvaluationAnalysisTest {

    private static final Path ANALYSIS_DIR = Path.of("analysis", "software-architecture");
    private static final Path SCHEMA_FILE = ANALYSIS_DIR.resolve("results/HotspotEvaluation.json");
    private static final Path PROMPT_FILE = ANALYSIS_DIR.resolve("prompts/hotspot_evaluation.md");
    private static final Path TASKS_FILE = ANALYSIS_DIR.resolve("tasks.yaml");

    /**
     * The response properties of the evaluation analyses. The hotspot evaluation reads the counted
     * hotspots of the tool, so none of these may reach the prompt.
     */
    private static final List<String> EVALUATION_PROPERTIES = List.of(
            "cyclomaticComplexityEvaluationAll",
            "visibilityEvaluationAll",
            "inheritanceEvaluationAll",
            "methodComplexityEvaluationAll",
            "nestingDepthEvaluationAll",
            "fieldVisibilityEvaluationAll",
            "classCohesionEvaluationAll",
            "couplingEvaluationAll",
            "testCoverageEvaluationAll",
            "circularDependencyEvaluationAll",
            "methodCountEvaluationAll",
            "documentationRatioEvaluationAll",
            "dataClassEvaluationAll",
            "booleanParameterEvaluationAll",
            "annotationEvaluationAll",
            "packageTangleEvaluationAll",
            "importDiversityEvaluationAll",
            "domainVocabularyEvaluationAll",
            "godClassEvaluationAll",
            "interModuleDependencyEvaluation",
            "classDependencyGraphEvaluationAll",
            "domainGlossary",
            "modernisation_anchors");

    /**
     * The result of the hotspot tool for a project whose counted history cannot be read at all.
     */
    private static final String TOOL_RESULT_WITHOUT_HISTORY = """
            {
              "metric": "Hotspots",
              "hotspotsAvailable": false,
              "absence": "NOT_A_REPOSITORY",
              "reason": "The project directory is not inside a git repository.",
              "note": "The counted history of the project is absent, so the project holds no counted hotspots."
            }
            """;

    @Test
    void theHotspotSchemaDeclaresEveryPropertyItRequires() throws IOException {
        JsonNode schema = readSchema();

        assertEquals("HotspotEvaluation", schema.path("title").asText());
        assertEquals("object", schema.path("type").asText());
        assertEquals(List.of("reasoning", "history", "hotspots", "limits"), requiredFields(schema));
        assertEquals(List.of("available", "window", "truncated", "absence", "reason", "statement"),
                requiredFields(schema.path("properties").path("history")));
        assertEquals(List.of("className", "module", "rank", "changeCount", "complexity", "methodCount",
                        "reasoning"),
                requiredFields(schema.path("properties").path("hotspots").path("items")));

        for (String required : requiredFields(schema)) {
            assertTrue(schema.path("properties").has(required),
                    "the schema must declare the property it requires: " + required);
        }
    }

    @Test
    void theHotspotSchemaIsAValidJsonSchema() throws IOException {
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

    @Test
    void aRenderedSampleAnswerValidatesAgainstTheHotspotSchema() throws IOException {
        String rendered = render();
        assertTrue(rendered.contains("java_get_hotspots"),
                "the rendered prompt must name the tool it calls, got:\n" + rendered);

        String answer = """
                {
                  "reasoning": "The counted hotspots show the classes of this project that change often and that are complicated.",
                  "history": {
                    "available": true,
                    "window": "The last 500 commits",
                    "truncated": false,
                    "absence": "",
                    "reason": "",
                    "statement": "The change count is counted in the counted window of the history."
                  },
                  "hotspots": [
                    {
                      "className": "com.example.core.OrderService",
                      "module": "core",
                      "rank": 1,
                      "changeCount": 12,
                      "complexity": 94,
                      "methodCount": 11,
                      "reasoning": "The class changed in 12 commits and carries a complexity of 94, so it leads the list of the classes of this project."
                    }
                  ],
                  "limits": [
                    "The change frequency counts the commits of the counted window of the history.",
                    "A class carries the change count of its file.",
                    "No ownership and no age of the code are part of this analysis."
                  ]
                }
                """;

        List<String> violations = violations(answer);

        assertTrue(violations.isEmpty(), "the sample answer must validate, got: " + violations);
    }

    @Test
    void anAnswerWithoutAHistoryNamesTheAbsenceWithItsReason() throws IOException {
        JsonNode toolResult = new ObjectMapper().readTree(TOOL_RESULT_WITHOUT_HISTORY);

        String answer = """
                {
                  "reasoning": "The counted history of the project is absent, so the project holds no counted hotspots. That is not the same as a project without hotspots.",
                  "history": {
                    "available": false,
                    "window": "",
                    "truncated": false,
                    "absence": "NOT_A_REPOSITORY",
                    "reason": "The project directory is not inside a git repository.",
                    "statement": "The counted history of the project is absent, so no hotspot can be counted."
                  },
                  "hotspots": [],
                  "limits": [
                    "The change frequency counts the commits of the counted window of the history.",
                    "A class carries the change count of its file.",
                    "No ownership and no age of the code are part of this analysis."
                  ]
                }
                """;

        assertTrue(violations(answer).isEmpty(),
                "an answer that reports the absence must validate, got: " + violations(answer));

        JsonNode parsed = new ObjectMapper().readTree(answer);
        JsonNode history = parsed.path("history");

        assertFalse(history.path("available").asBoolean(),
                "the answer must report that the counted history is absent");
        assertEquals(toolResult.path("absence").asText(), history.path("absence").asText(),
                "the answer must name the same case of the absent history as the tool");
        assertEquals(toolResult.path("reason").asText(), history.path("reason").asText(),
                "the answer must name the reason of the absent history as the tool gave it");
        assertTrue(parsed.path("hotspots").isEmpty(),
                "the answer must hold no hotspot, because the history is absent");
        assertTrue(parsed.path("reasoning").asText().contains("no counted hotspots"),
                "the reasoning must state that the project holds no counted hotspots, got: "
                        + parsed.path("reasoning").asText());
    }

    @Test
    void theHotspotPromptCallsOnlyTheToolOfTheWhitelist() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);
        String task = extractBlock(Files.readString(TASKS_FILE), "id: HotspotEvaluation");

        Set<String> calledTools = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\b(?:java|sbom)_get_[a-z_]+").matcher(prompt);
        while (matcher.find()) {
            calledTools.add(matcher.group());
        }

        assertEquals(Set.of("java_get_hotspots"), calledTools,
                "the prompt must call the hotspot tool and nothing else, got: " + calledTools);
        assertEquals(calledTools, whitelistOf(task),
                "the whitelist must hold exactly the tools the prompt calls");
    }

    @Test
    void theHotspotPromptNamesTheTwoFiguresTheRankAndTheLimits() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("The two figures the join holds are the change count of the file"),
                "the prompt must name the two figures the join holds, got:\n" + prompt);
        assertTrue(prompt.contains("Neither figure alone makes a hotspot"),
                "the prompt must state that neither figure alone makes a hotspot, got:\n" + prompt);
        assertTrue(prompt.contains("It is a rank among the classes of this project, not a score, not a threshold"),
                "the prompt must state that the rank is a rank and not a threshold, got:\n" + prompt);
        assertTrue(prompt.contains("The change frequency counts the commits of the counted window"),
                "the prompt must state the counted window as the bound of the change frequency, got:\n" + prompt);
        assertTrue(prompt.contains("A class carries the change count of its file"),
                "the prompt must state that a class carries the change count of its file, got:\n" + prompt);
        assertTrue(prompt.contains("A truncated history leaves the classes that were changed before the counted window out"),
                "the prompt must state that a truncated history leaves the older classes out, got:\n" + prompt);
        assertTrue(prompt.contains("No ownership and no age of the code are part of this analysis"),
                "the prompt must state that no ownership and no age of the code are part of the analysis, got:\n" + prompt);
        assertTrue(prompt.contains("the absence and its reason are the result of the analysis, not an empty list of hotspots"),
                "the prompt must report the absence of the history instead of an empty evaluation, got:\n" + prompt);
    }

    @Test
    void theHotspotPromptReadsNoFindingOfAnotherEvaluation() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        for (String property : EVALUATION_PROPERTIES) {
            assertFalse(prompt.contains(property),
                    "the prompt must not read the result of another evaluation: " + property);
        }

        assertFalse(prompt.contains("{{analysis."), "the prompt must not read the whole analysis state");
        assertFalse(prompt.contains("{{#with "), "the prompt must not read the whole analysis state");
    }

    @Test
    void theHotspotPromptNamesItsOwnSchema() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("Use the `HotspotEvaluation` response schema."),
                "the prompt must name the schema of this task, got:\n" + prompt);
        assertFalse(prompt.contains("ModuleBatchEvaluation"),
                "the hotspot evaluation does not use the shape of a module evaluation, got:\n" + prompt);
    }

    @Test
    void theHotspotTaskIsActiveAndDependsOnTheRawModuleReports() throws IOException {
        String task = extractBlock(Files.readString(TASKS_FILE), "id: HotspotEvaluation");

        assertTrue(task.contains("active: true"), "the task must be active");
        assertTrue(task.contains("responseProperty: hotspotEvaluation"));
        assertTrue(task.contains("responseFormat: results/HotspotEvaluation.json"));
        assertTrue(task.contains("prompt: prompts/hotspot_evaluation.md"));
        assertTrue(task.contains("systemPrompt: prompts/systemprompt.md"));
        assertTrue(task.contains("tags:\n  - hotspot_evaluation"));
        assertTrue(task.contains("  - module_analysis_reports_all\n"),
                "the task must depend on the raw module reports, got:\n" + task);
        assertFalse(task.contains("loopOn:"), "the evaluation is one call for the project");
    }

    @TempDir
    Path tempDir;

    @Test
    void theAnalysisDomainLoadsWithTheHotspotTask() throws IOException {
        TaskManager taskManager = TaskManager.initializeTasks(ANALYSIS_DIR, tempDir, Set.of());

        assertTrue(taskManager != null, "the task definitions of the analysis domain must validate");

        TaskDefinition task = taskManager.getAllTasks().stream()
                .filter(candidate -> candidate.getId().equals("HotspotEvaluation"))
                .findFirst()
                .orElseThrow();

        assertTrue(task.isActive(), "the hotspot task must be active");
        assertEquals("hotspotEvaluation", task.getResponseProperty());
    }

    private static JsonNode readSchema() throws IOException {
        return new ObjectMapper().readTree(SCHEMA_FILE.toFile());
    }

    private static List<String> violations(String answer) throws IOException {
        SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(
                Dialects.getDraft202012(),
                builder -> builder.nodeReader(DefaultNodeReader.Builder::locationAware));
        Schema validator = schemaRegistry.getSchema(readSchema().toString(), InputFormat.JSON);

        return validator.validate(answer, InputFormat.JSON).stream()
                .map(com.networknt.schema.Error::getMessage)
                .toList();
    }

    private static String render() throws IOException {
        var handlebars = HandlebarsFactory.create()
                .with(new FileTemplateLoader(ANALYSIS_DIR.toString(), ""));
        Template template = handlebars.compileInline(Files.readString(PROMPT_FILE));

        return template.apply(new HashMap<String, Object>());
    }

    private static Set<String> whitelistOf(String taskBlock) {
        Set<String> tools = new LinkedHashSet<>();
        boolean inWhitelist = false;

        for (String line : taskBlock.split("\n")) {
            if (line.startsWith("toolWhitelist:")) {
                inWhitelist = true;
                continue;
            }
            if (inWhitelist) {
                if (!line.startsWith("  - ")) {
                    break;
                }
                tools.add(line.substring("  - ".length()).trim());
            }
        }

        return tools;
    }

    private static String extractBlock(String yaml, String marker) {
        int start = yaml.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException(marker + " not found");
        }
        int nextSeparator = yaml.indexOf("\n---", start + marker.length());

        return yaml.substring(start, nextSeparator < 0 ? yaml.length() : nextSeparator);
    }

    private static List<String> requiredFields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.path("required").forEach(field -> fields.add(field.asText()));

        return fields;
    }
}
