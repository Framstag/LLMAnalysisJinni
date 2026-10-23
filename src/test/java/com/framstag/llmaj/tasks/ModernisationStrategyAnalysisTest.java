package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.JsonNodeModelWrapper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.serialization.DefaultNodeReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Covers the modernisation strategy analysis: the prompt and the task entry of the analysis domain of
 * the project.
 * <p>
 * The analysis is the place where the anchors, the hotspots and the counted evidence are joined into one
 * list of modernisation areas. The tests hold it to the promises of the design: the patterns of an area
 * come from the catalogue, the cost of an area rests on counted figures that are named beside it, the
 * order of the areas is a cost order and not a decision, an area type whose input analysis did not run
 * is reported as absent, and the input of the analysis is bounded, so that its size does not grow with
 * the number of findings of the project.
 */
class ModernisationStrategyAnalysisTest {

    private static final Path ANALYSIS_DIR = Path.of("analysis", "software-architecture");
    private static final Path SCHEMA_FILE = ANALYSIS_DIR.resolve("results/ModernisationStrategy.json");
    private static final Path PROMPT_FILE = ANALYSIS_DIR.resolve("prompts/modernisation_strategy.md");
    private static final Path TASKS_FILE = ANALYSIS_DIR.resolve("tasks.yaml");

    private static final List<String> CATALOGUE_PATTERNS = List.of(
            "strangler-fig",
            "branch-by-abstraction",
            "module-extraction",
            "anticorruption-layer",
            "horizontal-lift",
            "retirement");

    private static final List<String> ANCHOR_TYPES = List.of(
            "entry-point",
            "seam",
            "domain",
            "build-unit",
            "cut-point",
            "test-coverage",
            "technology-marker");

    /**
     * The response properties of the evaluation analyses. The strategy analysis reads the anchor result,
     * the hotspot result and the counted evidence, so none of these may reach the prompt.
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

    /**
     * The state of a run that holds the anchor result and the hotspot result.
     */
    private static final String STATE_WITH_HOTSPOTS = """
            {
              "modernisation_anchors": {
                "reasoning": "The anchors follow from the counted evidence.",
                "anchors": [
                  {
                    "anchorType": "seam",
                    "module": "core",
                    "element": "com.example.core.OrderPort",
                    "evidence": [
                      "The seam analysis counted 3 implementors of the interface."
                    ],
                    "rank": 1,
                    "candidatePatterns": [
                      "branch-by-abstraction"
                    ],
                    "reasoning": "The interface is the widest abstraction of the module."
                  }
                ],
                "absentTypes": []
              },
              "hotspotEvaluation": {
                "reasoning": "The counted hotspots show the classes that change often and that are complicated.",
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
                    "reasoning": "The class changed in 12 commits and carries a complexity of 94."
                  }
                ],
                "limits": [
                  "The change frequency counts the commits of the counted window of the history."
                ]
              }
            }
            """;

    /**
     * The state of a run in which the hotspot analysis did not run.
     */
    private static final String STATE_WITHOUT_HOTSPOTS = """
            {
              "modernisation_anchors": {
                "reasoning": "The anchors follow from the counted evidence.",
                "anchors": [
                  {
                    "anchorType": "seam",
                    "module": "core",
                    "element": "com.example.core.OrderPort",
                    "evidence": [
                      "The seam analysis counted 3 implementors of the interface."
                    ],
                    "rank": 1,
                    "candidatePatterns": [
                      "branch-by-abstraction"
                    ],
                    "reasoning": "The interface is the widest abstraction of the module."
                  }
                ],
                "absentTypes": []
              }
            }
            """;

    @Test
    void theStrategyPromptNamesTheMarkerFactsTheInputsAndTheTools() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("{{> facts/modernisation_markers.md}}"),
                "the prompt must take the pattern catalogue from the marker facts, got:\n" + prompt);
        assertTrue(prompt.contains("{{#modernisation_anchors.anchors}}"),
                "the prompt must read the anchors of the anchor result, got:\n" + prompt);
        assertTrue(prompt.contains("{{#hotspotEvaluation.hotspots}}"),
                "the prompt must read the hotspots of the hotspot result, got:\n" + prompt);

        for (String tool : List.of("java_get_inter_module_dependency_report",
                "java_get_all_split_candidates",
                "java_get_all_test_coverage_reports")) {
            assertTrue(prompt.contains("**`" + tool + "`**"),
                    "the prompt must name the counted tool " + tool + ", got:\n" + prompt);
        }
    }

    @Test
    void theStrategyPromptCallsOnlyTheToolsOfTheWhitelist() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);
        String task = extractBlock(Files.readString(TASKS_FILE), "id: ModernisationStrategy");

        Set<String> calledTools = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\b(?:java|sbom)_get_[a-z_]+").matcher(prompt);
        while (matcher.find()) {
            calledTools.add(matcher.group());
        }

        assertEquals(3, calledTools.size(), "the prompt must call the three counted tools, got: " + calledTools);
        assertEquals(calledTools, whitelistOf(task),
                "the whitelist must hold exactly the tools the prompt calls");
    }

    @Test
    void theStrategyPromptReadsNoFindingOfAnotherEvaluation() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        for (String property : EVALUATION_PROPERTIES) {
            assertFalse(prompt.contains(property),
                    "the prompt must not read the result of another evaluation: " + property);
        }

        assertFalse(prompt.contains("{{analysis."), "the prompt must not read the whole analysis state");
        assertFalse(prompt.contains("{{#with "), "the prompt must not read the whole analysis state");
    }

    @Test
    void theStrategyPromptNamesTheCatalogueAndTheEvidenceOfEveryPattern() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        for (String pattern : CATALOGUE_PATTERNS) {
            assertTrue(prompt.contains("`" + pattern + "`"),
                    "the prompt must name the catalogue pattern " + pattern + ", got:\n" + prompt);
        }

        for (String anchorType : ANCHOR_TYPES) {
            assertTrue(prompt.contains("`" + anchorType + "`"),
                    "the prompt must name the anchor type " + anchorType + ", got:\n" + prompt);
        }

        assertTrue(prompt.contains("A pattern that the catalogue does not hold is not named"),
                "the prompt must hold the patterns to the catalogue, got:\n" + prompt);
        assertTrue(prompt.contains("the `evidenceFor` holds the counted evidence that supports the pattern"),
                "the prompt must require the evidence for a pattern, got:\n" + prompt);
        assertTrue(prompt.contains("The `evidenceAgainst` holds the counted evidence that counts against"),
                "the prompt must require the evidence against a pattern, got:\n" + prompt);
        assertTrue(prompt.contains("no evidence against that pattern was found"),
                "the prompt must state that a pattern without evidence against it says so, got:\n" + prompt);
        assertTrue(prompt.contains("what would have to be true for the pattern to be the wrong choice"),
                "the prompt must require what would make a pattern the wrong choice, got:\n" + prompt);
    }

    @Test
    void theStrategyPromptNamesTheFiguresOfTheCostAndTheMissingOnes() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("the afferent coupling of the module, the price of the cut of the module and the test coverage of the area"),
                "the prompt must name the figures the cost rests on, got:\n" + prompt);
        assertTrue(prompt.contains("its value as the counted analysis reports it, and its source"),
                "the prompt must require the name, the value and the source of a figure, got:\n" + prompt);
        assertTrue(prompt.contains("A figure that the counted evidence does not hold is not stated as a value"),
                "the prompt must state that a missing figure is not stated, got:\n" + prompt);
        assertTrue(prompt.contains("names the missing figure in its reasoning"),
                "the prompt must require the missing figure to be named, got:\n" + prompt);
    }

    @Test
    void theStrategyPromptStatesThatTheOrderIsACostOrderAndNotADecision() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("The areas are ordered by the cost the counted evidence implies"),
                "the prompt must state that the order follows the counted cost, got:\n" + prompt);
        assertTrue(prompt.contains("The order is not a decision"),
                "the prompt must state that the order is not a decision, got:\n" + prompt);
        assertTrue(prompt.contains("The analysis names no area as the one to start with"),
                "the prompt must name no area to start with, got:\n" + prompt);
        assertTrue(prompt.contains("it holds no knowledge of the business value of a change"),
                "the prompt must state that the business value is not part of the analysis, got:\n" + prompt);
    }

    @Test
    void theStrategyPromptStatesTheLimitsOfTheAnalysis() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("The cost of an area rests on the counted evidence of the Java modules alone"),
                "the prompt must state what the cost of an area rests on, got:\n" + prompt);
        assertTrue(prompt.contains("The analysis knows no business value of a change"),
                "the prompt must state that the analysis knows no business value, got:\n" + prompt);
        assertTrue(prompt.contains("It ranks by the counted cost only"),
                "the prompt must state that the analysis ranks by the counted cost only, got:\n" + prompt);
        assertTrue(prompt.contains("The `reasoning` of the answer holds the limits of the analysis"),
                "the prompt must require the limits to be part of the answer, got:\n" + prompt);
    }

    @Test
    void theStrategyPromptNamesItsOwnSchema() throws IOException {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("Use the `ModernisationStrategy` response schema."),
                "the prompt must name the schema of this task, got:\n" + prompt);
        assertFalse(prompt.contains("ModuleBatchEvaluation"),
                "the strategy analysis does not use the shape of a module evaluation, got:\n" + prompt);
    }

    @Test
    void theStrategyPromptStatesTheAbsentHotspotTypeWhenTheHotspotAnalysisIsMissing() throws IOException {
        String rendered = render(STATE_WITHOUT_HOTSPOTS);

        assertTrue(rendered.contains("The hotspot analysis did not run"),
                "the prompt must state that the hotspot analysis did not run, got:\n" + rendered);
        assertTrue(rendered.contains("The areas that rest on the hotspots are absent"),
                "the prompt must state that the areas that rest on the hotspots are absent, got:\n" + rendered);
        assertTrue(rendered.contains("`hotspot`"),
                "the prompt must name the absent type, got:\n" + rendered);
    }

    @Test
    void theStrategyPromptRendersTheAnchorsAndTheHotspotsWhenTheyArePresent() throws IOException {
        String rendered = render(STATE_WITH_HOTSPOTS);

        assertTrue(rendered.contains("com.example.core.OrderPort"),
                "the prompt must name the element of the anchor, got:\n" + rendered);
        assertTrue(rendered.contains("com.example.core.OrderService"),
                "the prompt must name the class of the hotspot, got:\n" + rendered);
        assertFalse(rendered.contains("The hotspot analysis did not run"),
                "the prompt must not state an absence that does not hold, got:\n" + rendered);
    }

    @Test
    void aResultWithoutHotspotsReportsTheHotspotAreasAsAbsentWithTheReason() throws IOException {
        String answer = """
                {
                  "reasoning": "The areas follow from the anchors and the counted evidence. The order follows the counted cost and the choice of the area to work on is the reader's. The analysis knows no business value of a change and ranks by the counted cost only.",
                  "areas": [
                    {
                      "module": "core",
                      "element": "com.example.core.OrderPort",
                      "anchorType": "seam",
                      "patterns": [
                        {
                          "name": "branch-by-abstraction",
                          "evidenceFor": [
                            "The seam analysis counted 3 implementors of the interface."
                          ],
                          "evidenceAgainst": [],
                          "whatWouldMakeItWrong": "No abstraction can be named that the callers would use."
                        }
                      ],
                      "costFigures": [
                        {
                          "name": "afferent coupling of the module",
                          "value": "12 incoming references",
                          "source": "The inter module dependency analysis counted the references of the module."
                        }
                      ],
                      "cost": "A change of the interface reaches the 12 modules that import it.",
                      "reasoning": "The interface is the widest abstraction of the module. No evidence against the pattern branch-by-abstraction was found."
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

        assertTrue(violations.isEmpty(), "the result must validate, got: " + violations);

        JsonNode parsed = new ObjectMapper().readTree(answer);
        JsonNode hotspot = null;
        for (JsonNode absent : parsed.path("absentTypes")) {
            if ("hotspot".equals(absent.path("type").asText())) {
                hotspot = absent;
            }
        }

        assertTrue(hotspot != null, "the result must report the hotspot areas as absent");
        assertTrue(hotspot.path("reason").asText().contains("hotspot analysis"),
                "the reason must name the analysis that did not run, got: " + hotspot.path("reason").asText());
        assertFalse(parsed.path("areas").isEmpty(),
                "the areas that rest on the other evidence must still be reported");
    }

    @Test
    void theStrategyTaskIsActiveAndDependsOnTheAnalysesItsAreasRestOn() throws IOException {
        String task = extractBlock(Files.readString(TASKS_FILE), "id: ModernisationStrategy");

        assertTrue(task.contains("active: true"), "the task must be active");
        assertTrue(task.contains("responseProperty: modernisationStrategy"));
        assertTrue(task.contains("responseFormat: results/ModernisationStrategy.json"));
        assertTrue(task.contains("prompt: prompts/modernisation_strategy.md"));
        assertTrue(task.contains("systemPrompt: prompts/systemprompt.md"));
        assertTrue(task.contains("tags:\n  - modernisation_strategy_evaluation"));

        for (String dependencyTag : List.of(
                "modernisation_anchor_evaluation",
                "hotspot_evaluation",
                "inter_module_dependency_evaluation",
                "class_dependency_graph_evaluation_all",
                "test_coverage_evaluation_all")) {
            assertTrue(task.contains("  - " + dependencyTag + "\n"),
                    "the task must depend on the tag " + dependencyTag + ", got:\n" + task);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void theAnalysisDomainLoadsWithTheStrategyTask() throws IOException {
        TaskManager taskManager = TaskManager.initializeTasks(ANALYSIS_DIR, tempDir, Set.of());

        assertTrue(taskManager != null, "the task definitions of the analysis domain must validate");

        TaskDefinition task = taskManager.getAllTasks().stream()
                .filter(candidate -> candidate.getId().equals("ModernisationStrategy"))
                .findFirst()
                .orElseThrow();

        assertTrue(task.isActive(), "the strategy task must be active");
        assertEquals("modernisationStrategy", task.getResponseProperty());
    }

    private static List<String> violations(String answer) throws IOException {
        SchemaRegistry schemaRegistry = SchemaRegistry.withDialect(
                Dialects.getDraft202012(),
                builder -> builder.nodeReader(DefaultNodeReader.Builder::locationAware));
        Schema validator = schemaRegistry.getSchema(
                Files.readString(SCHEMA_FILE), InputFormat.JSON);

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
}
