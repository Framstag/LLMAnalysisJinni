package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
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
 * Covers the modernisation anchor analysis: the response schema, the prompt and the task entry of the
 * analysis domain of the project.
 * <p>
 * The analysis is the place where the counted evidence of the project is joined into one ranked list
 * of the places a modernisation can attach to. The tests hold it to the three promises of the design:
 * the response names the evidence behind every number, the strength of an anchor is a rank and not a
 * verdict, and the input of the analysis is the counted evidence and the context map result, so that
 * the size of the input does not grow with the number of findings of the project.
 */
class ModernisationAnchorAnalysisTest {

    private static final Path ANALYSIS_DIR = Path.of("analysis", "software-architecture");
    private static final Path SCHEMA_FILE = ANALYSIS_DIR.resolve("results/ModernisationAnchors.json");
    private static final Path PROMPT_FILE = ANALYSIS_DIR.resolve("prompts/modernisation_anchors.md");
    private static final Path TASKS_FILE = ANALYSIS_DIR.resolve("tasks.yaml");

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

    /**
     * The response properties of the evaluation analyses. The anchor analysis reads the counted
     * evidence and the context map result, so none of these may reach the prompt.
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
            "domainGlossary");

    private static final String STATE_WITH_CONTEXT_MAP = """
            {
              "contextMap": {
                "reasoning": "The contexts follow from the shared vocabulary.",
                "contexts": [
                  {
                    "name": "orders",
                    "modules": ["core", "api"],
                    "language": ["order", "customer"]
                  }
                ],
                "relations": [
                  {
                    "fromContext": "orders",
                    "toContext": "billing",
                    "relation": "customer/supplier",
                    "evidence": "The pair shares 2 leading terms and imports each other once.",
                    "sharedLanguage": true,
                    "structurallyCoupled": true
                  }
                ],
                "unplacedModules": [
                  {
                    "module": "batch",
                    "reason": "Its vocabulary is too small to judge."
                  }
                ]
              }
            }
            """;

    private static final String STATE_WITHOUT_CONTEXT_MAP = """
            {
              "modules": {
                "modules": [
                  {
                    "name": "core",
                    "path": "core",
                    "root": false
                  }
                ]
              }
            }
            """;

    @Test
    void theAnchorSchemaDeclaresEveryPropertyItRequires() throws IOException {
        JsonNode schema = readSchema();

        assertEquals("ModernisationAnchors", schema.path("title").asText());
        assertEquals("object", schema.path("type").asText());
        assertEquals(List.of("reasoning", "anchors", "absentTypes"), requiredFields(schema));
        assertEquals(List.of("anchorType", "module", "element", "evidence", "rank", "candidatePatterns",
                        "reasoning"),
                requiredFields(schema.path("properties").path("anchors").path("items")));
        assertEquals(List.of("anchorType", "reason"),
                requiredFields(schema.path("properties").path("absentTypes").path("items")));

        for (String required : requiredFields(schema)) {
            assertTrue(schema.path("properties").has(required),
                    "the schema must declare the property it requires: " + required);
        }
    }

    @Test
    void theAnchorSchemaBoundsTheAnchorTypesAndThePatternCatalogue() throws IOException {
        JsonNode schema = readSchema();
        JsonNode anchor = schema.path("properties").path("anchors").path("items");

        assertEquals(ANCHOR_TYPES, stringList(anchor.path("properties").path("anchorType").path("enum")));
        assertEquals(CATALOGUE_PATTERNS, stringList(anchor.path("properties")
                .path("candidatePatterns")
                .path("items")
                .path("enum")));
        assertEquals(ANCHOR_TYPES, stringList(schema.path("properties")
                .path("absentTypes")
                .path("items")
                .path("properties")
                .path("anchorType")
                .path("enum")));
        assertEquals("array", schema.path("properties").path("anchors").path("type").asText());
        assertEquals("number", anchor.path("properties").path("rank").path("type").asText());
    }

    @Test
    void aRenderedSampleAnswerValidatesAgainstTheAnchorSchema() throws IOException {
        String answer = """
                {
                  "reasoning": "The anchors follow from the counted evidence of the Java modules and the context map.",
                  "anchors": [
                    {
                      "anchorType": "seam",
                      "module": "core",
                      "element": "com.example.core.OrderPort",
                      "evidence": [
                        "The seam analysis counted 3 implementors of the interface.",
                        "The seam analysis counted 2 modules that import the interface."
                      ],
                      "rank": 1,
                      "candidatePatterns": [
                        "branch-by-abstraction"
                      ],
                      "reasoning": "The interface is the widest abstraction of the module. Branch by abstraction would be the wrong choice when no abstraction can be named that the callers would use."
                    },
                    {
                      "anchorType": "domain",
                      "module": "",
                      "element": "orders -> billing",
                      "evidence": [
                        "The context map analysis counted a customer/supplier relation between the two contexts.",
                        "The context map analysis counted 2 shared leading terms for the pair."
                      ],
                      "rank": 1,
                      "candidatePatterns": [
                        "anticorruption-layer"
                      ],
                      "reasoning": "The relation is a boundary that carries a translation. An anti-corruption layer would be the wrong choice when the translation stays a pass through."
                    }
                  ],
                  "absentTypes": []
                }
                """;

        List<String> violations = violations(answer);

        assertTrue(violations.isEmpty(), "the sample answer must validate, got: " + violations);
    }

    @Test
    void anAnswerWithoutAContextMapReportsTheDomainAnchorsAsAbsentWithTheReason() throws IOException {
        String answer = """
                {
                  "reasoning": "The Java modules were counted, the context map analysis did not run.",
                  "anchors": [
                    {
                      "anchorType": "entry-point",
                      "module": "api",
                      "element": "com.example.api.Application",
                      "evidence": [
                        "The entry point analysis counted 1 class with a main method in the module api."
                      ],
                      "rank": 1,
                      "candidatePatterns": [],
                      "reasoning": "The class starts the process. No pattern of the catalogue fits it, because the process itself is not a legacy part."
                    }
                  ],
                  "absentTypes": [
                    {
                      "anchorType": "domain",
                      "reason": "The context map analysis did not run, so no relation between the contexts is available."
                    }
                  ]
                }
                """;

        assertTrue(violations(answer).isEmpty(),
                "an answer that reports an absent type must validate, got: " + violations(answer));

        JsonNode parsed = new ObjectMapper().readTree(answer);
        JsonNode domain = null;
        for (JsonNode absent : parsed.path("absentTypes")) {
            if ("domain".equals(absent.path("anchorType").asText())) {
                domain = absent;
            }
        }

        assertTrue(domain != null, "the answer must report the domain anchors as absent");
        assertTrue(domain.path("reason").asText().contains("context map"),
                "the reason must name the analysis that did not run, got: " + domain.path("reason").asText());
    }

    @Test
    void theAnchorPromptNamesTheMarkerFactsTheToolsAndEveryAnchorType() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("{{> facts/modernisation_markers.md}}"),
                "the prompt must take the marker knowledge from the marker facts, got:\n" + prompt);

        for (String anchorType : ANCHOR_TYPES) {
            assertTrue(prompt.contains("`" + anchorType + "`"),
                    "the prompt must name the anchor type " + anchorType + ", got:\n" + prompt);
        }

        assertTrue(prompt.contains("**`java_get_anchor_candidates`**"),
                "the prompt must name the bounded candidate tool, got:\n" + prompt);

        for (String pattern : CATALOGUE_PATTERNS) {
            assertTrue(prompt.contains("`" + pattern + "`"),
                    "the prompt must name the catalogue pattern " + pattern + ", got:\n" + prompt);
        }
    }

    @Test
    void theAnchorPromptCallsOnlyTheToolsOfTheWhitelist() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);
        String task = extractBlock(Files.readString(TASKS_FILE), "id: ModernisationAnchors");

        Set<String> calledTools = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\b(?:java|sbom)_get_[a-z_]+").matcher(prompt);
        while (matcher.find()) {
            calledTools.add(matcher.group());
        }

        assertEquals(Set.of("java_get_anchor_candidates"), calledTools,
                "the prompt must call the one bounded candidate tool and no other, got: " + calledTools);
        assertEquals(calledTools, whitelistOf(task),
                "the whitelist must hold exactly the tools the prompt calls");
    }

    @Test
    void theAnchorPromptReadsNoFindingOfAnotherEvaluation() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        for (String property : EVALUATION_PROPERTIES) {
            assertFalse(prompt.contains(property),
                    "the prompt must not read the result of another evaluation: " + property);
        }

        assertFalse(prompt.contains("{{analysis."), "the prompt must not read the whole analysis state");
        assertFalse(prompt.contains("{{#with analysis."), "the prompt must not read the whole analysis state");
        assertFalse(prompt.contains("{{#with "), "the prompt must not read the whole analysis state");
    }

    @Test
    void theAnchorPromptReadsTheContextMapBounded() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("{{#contextMap.contexts}}"),
                "the prompt must read the contexts of the context map, got:\n" + prompt);
        assertTrue(prompt.contains("{{#contextMap.relations}}"),
                "the prompt must read the relations of the context map, got:\n" + prompt);
        assertFalse(prompt.contains("unplacedModules"),
                "the prompt must read the contexts and the relations, nothing else, got:\n" + prompt);
    }

    @Test
    void theAnchorPromptStatesTheRankTheCandidatesAndTheLimits() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("RANK among the anchors of its type in this project"),
                "the prompt must state that the strength of an anchor is a rank, got:\n" + prompt);
        assertTrue(prompt.contains("no anchor is good or bad"),
                "the prompt must state that no anchor is good or bad, got:\n" + prompt);
        assertTrue(prompt.contains("no absolute threshold decides anything"),
                "the prompt must state that no absolute threshold decides anything, got:\n" + prompt);
        assertTrue(prompt.contains("what would make that pattern the wrong choice"),
                "the prompt must require what would make a candidate pattern the wrong choice, got:\n" + prompt);
        assertTrue(prompt.contains("No change frequency, no age of the code and no ownership"),
                "the prompt must state the limits of the analysis, got:\n" + prompt);
        assertTrue(prompt.contains("counted evidence of the Java modules alone"),
                "the prompt must state what the anchors rest on, got:\n" + prompt);
        assertTrue(prompt.contains("absentTypes"),
                "the prompt must report an absent anchor type, got:\n" + prompt);
        assertTrue(prompt.contains("candidatesPerType"),
                "the prompt must state the budget that bounds the candidate lists, got:\n" + prompt);
        assertTrue(prompt.contains("say in the `reasoning` of the answer that the list was bounded"),
                "the prompt must require that a bounded list is named as bounded, got:\n" + prompt);
    }

    @Test
    void theAnchorPromptNamesItsOwnSchema() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("Use the `ModernisationAnchors` response schema."),
                "the prompt must name the schema of this task, got:\n" + prompt);
        assertFalse(prompt.contains("ModuleBatchEvaluation"),
                "the anchor analysis does not use the shape of a module evaluation, got:\n" + prompt);
    }

    @Test
    void theAnchorPromptStatesTheAbsentDomainTypeWhenTheContextMapIsMissing() throws IOException {
        String rendered = render(STATE_WITHOUT_CONTEXT_MAP);

        assertTrue(rendered.contains("The context map analysis did not run"),
                "the prompt must state that the context map did not run, got:\n" + rendered);
        assertTrue(rendered.contains("absentTypes"),
                "the prompt must report the absent type, got:\n" + rendered);
    }

    @Test
    void theAnchorPromptRendersTheContextMapWhenItIsPresent() throws IOException {
        String rendered = render(STATE_WITH_CONTEXT_MAP);

        assertTrue(rendered.contains("orders"), "the prompt must name the contexts, got:\n" + rendered);
        assertTrue(rendered.contains("\"orders\" -> \"billing\""),
                "the prompt must name the relations, got:\n" + rendered);
        assertFalse(rendered.contains("The context map analysis did not run"),
                "the prompt must not state an absence that does not hold, got:\n" + rendered);
        assertFalse(rendered.contains("Its vocabulary is too small to judge."),
                "the prompt must not read the unplaced modules of the context map, got:\n" + rendered);
    }

    @Test
    void theAnchorTaskIsActiveAndDependsOnTheAnalysesItsTypesRestOn() throws IOException {
        String task = extractBlock(Files.readString(TASKS_FILE), "id: ModernisationAnchors");

        assertTrue(task.contains("active: true"), "the task must be active");
        assertTrue(task.contains("responseProperty: modernisation_anchors"));
        assertTrue(task.contains("responseFormat: results/ModernisationAnchors.json"));
        assertTrue(task.contains("prompt: prompts/modernisation_anchors.md"));
        assertTrue(task.contains("systemPrompt: prompts/systemprompt.md"));
        assertTrue(task.contains("tags:\n  - modernisation_anchor_evaluation"));

        for (String dependencyTag : List.of(
                "context_map_evaluation",
                "inter_module_dependency_evaluation",
                "class_dependency_graph_evaluation_all",
                "test_coverage_evaluation_all",
                "module_analysis_reports_all",
                "dependencies")) {
            assertTrue(task.contains("  - " + dependencyTag + "\n"),
                    "the task must depend on the tag " + dependencyTag + ", got:\n" + task);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void theAnchorSchemaIsAValidJsonSchema() throws IOException {
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
    void theAnalysisDomainLoadsWithTheAnchorTask() throws IOException {
        TaskManager taskManager = TaskManager.initializeTasks(ANALYSIS_DIR, tempDir, Set.of());

        assertTrue(taskManager != null, "the task definitions of the analysis domain must validate");

        TaskDefinition task = taskManager.getAllTasks().stream()
                .filter(candidate -> candidate.getId().equals("ModernisationAnchors"))
                .findFirst()
                .orElseThrow();

        assertTrue(task.isActive(), "the anchor task must be active");
        assertEquals("modernisation_anchors", task.getResponseProperty());
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

    private static String render(String state) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode stateNode = mapper.readTree(state);

        Map<String, Object> model = new HashMap<>();
        model.putAll(new JsonNodeModelWrapper(stateNode));

        var handlebars = HandlebarsFactory.create()
                .with(new FileTemplateLoader(ANALYSIS_DIR.toString(), ""));
        Template template = handlebars.compileInline(Files.readString(PROMPT_FILE));

        return template.apply(model);
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

    private static List<String> stringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));

        return values;
    }
}
