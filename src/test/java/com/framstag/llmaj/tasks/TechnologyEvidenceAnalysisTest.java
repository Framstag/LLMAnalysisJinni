package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.handlebars.HandlebarsFactory;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the classification of the technology of a project from its own imports: the response schema, the prompt and
 * the task entry.
 *
 * <p>The analysis answers a question the dependency analyses cannot answer for a project without an SBOM. So the tests
 * hold it to what it may claim: a technology and no artifact, no version at all, and the marker knowledge in the
 * marker facts and not in the prompt of the analysis.
 */
class TechnologyEvidenceAnalysisTest {

    private static final Path ANALYSIS_DIR = Path.of("analysis", "software-architecture");
    private static final Path SCHEMA_FILE = ANALYSIS_DIR.resolve("results/TechnologyEvidence.json");
    private static final Path PROMPT_FILE = ANALYSIS_DIR.resolve("prompts/technology_evidence.md");
    private static final Path ANCHOR_PROMPT_FILE = ANALYSIS_DIR.resolve("prompts/modernisation_anchors.md");
    private static final Path TASKS_FILE = ANALYSIS_DIR.resolve("tasks.yaml");

    /**
     * The namespaces and version ranges the marker facts mark, one of each generation. None of them may appear in a
     * prompt of this analysis: which namespace or version range marks a legacy dependency is knowledge of the marker
     * facts and not of a prompt.
     */
    private static final List<String> MARKER_KNOWLEDGE = List.of(
            "javax.persistence", "javax.servlet", "javax.ws.rs", "jakarta.persistence",
            "2.17.1", "5.3.x", "1.2.x");

    /**
     * The modernisation patterns of the catalogue. The anchor prompt names them so that the answer cannot invent a
     * pattern outside the catalogue, so only the prompt that has no business with a pattern must be free of them.
     */
    private static final List<String> PATTERN_NAMES = List.of(
            "strangler-fig", "branch-by-abstraction", "module-extraction", "anticorruption-layer",
            "horizontal-lift", "retirement");

    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @Test
    void theSchemaClaimsNoVersion() throws Exception {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_FILE));

        List<String> versionProperties = new ArrayList<>();
        collectPropertyNames(schema, versionProperties);

        assertTrue(versionProperties.stream().noneMatch(name -> name.toLowerCase().contains("version")),
                "a field for a version is a field that gets filled, so the schema has none: " + versionProperties);

        assertEquals(List.of("reasoning", "limit", "technologies", "unplaced"),
                fieldNames(schema.get("required")),
                "every field of the answer is required, so a model cannot leave the limit out");
    }

    @Test
    void thePromptRendersAndCallsTheEvidenceToolAndNothingElse() throws Exception {
        String prompt = render();

        Set<String> calledTools = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\b(?:java|sbom)_get_[a-z_]+").matcher(prompt);
        while (matcher.find()) {
            calledTools.add(matcher.group());
        }

        assertEquals(Set.of("java_get_import_technology_evidence"), calledTools,
                "the prompt must call the evidence tool and nothing else, got: " + calledTools);
        assertEquals(calledTools, whitelistOf(taskBlock("TechnologyEvidence")),
                "the whitelist must hold exactly the tools the prompt calls");
    }

    @Test
    void thePromptNamesTheTechnologyRuleTheVersionRuleAndTheUnplacedRule() throws Exception {
        String prompt = Files.readString(PROMPT_FILE);

        assertTrue(prompt.contains("Name a technology and not an artifact"),
                "the prompt must state that a technology is named and an artifact is not");
        assertTrue(prompt.contains("State no version"),
                "the prompt must state that no version is part of the answer");
        assertTrue(prompt.contains("`unplaced`"),
                "the prompt must name the unplaced list");
        assertTrue(prompt.contains("totalGroups"),
                "the prompt must name the total beside the bounded list");
        assertTrue(prompt.contains("the `reason` the tool stated"),
                "an empty result is explained by the reason of the tool and not by a statement about the project");
    }

    @Test
    void theTaskStoresItsResultUnderItsPropertyAndDependsOnTheJavaReports() throws Exception {
        String task = taskBlock("TechnologyEvidence");

        assertTrue(task.contains("responseProperty: technologyEvidence"), task);
        assertTrue(task.contains("tags:\n  - technology_evidence"), task);
        assertTrue(task.contains("  - module_analysis_reports_all"), task);
        assertTrue(task.contains("active: true"), task);
        assertTrue(task.contains("responseFormat: results/TechnologyEvidence.json"), task);
    }

    /**
     * The specification of the anchors forbids a tool, and with it the prompt of the analysis, from holding the marker
     * knowledge. The consolidation supplies namespaces and counts; the facts decide which namespace marks a legacy
     * dependency.
     */
    @Test
    void neitherPromptHoldsTheMarkerKnowledge() throws Exception {
        for (Path promptFile : List.of(PROMPT_FILE, ANCHOR_PROMPT_FILE)) {
            String prompt = Files.readString(promptFile);

            for (String knowledge : MARKER_KNOWLEDGE) {
                assertFalse(prompt.contains(knowledge),
                        promptFile + " must not hold the marker knowledge '" + knowledge
                                + "', which belongs to the marker facts");
            }
        }

        String technologyPrompt = Files.readString(PROMPT_FILE);

        for (String pattern : PATTERN_NAMES) {
            assertFalse(technologyPrompt.contains(pattern),
                    "the classification of the technology of a project matches no modernisation pattern: " + pattern);
        }
    }

    @Test
    void theAnchorPromptReportsTheTwoHalvesOfTheTechnologyMarkerApart() throws Exception {
        var handlebars = HandlebarsFactory.create()
                .with(new FileTemplateLoader(ANALYSIS_DIR.toString(), ""));
        Template template = handlebars.compileInline(Files.readString(ANCHOR_PROMPT_FILE));
        String prompt = template.apply(new HashMap<String, Object>());

        assertTrue(prompt.contains("The type has two halves"),
                "the anchor prompt must name the two halves of the technology marker");
        assertTrue(prompt.contains("the version range half is unavailable"),
                "the anchor prompt must report the version range half as unavailable with the reason");
        assertTrue(prompt.contains("The type as a whole is absent only when both halves are missing"),
                "one missing half must not report the whole type as absent");
        assertTrue(prompt.contains("An anchor of the namespace half states no version"),
                "the namespace half carries no version, so an anchor of that half names none");
    }

    private String render() throws IOException {
        var handlebars = HandlebarsFactory.create()
                .with(new FileTemplateLoader(ANALYSIS_DIR.toString(), ""));
        Template template = handlebars.compileInline(Files.readString(PROMPT_FILE));

        return template.apply(new HashMap<String, Object>());
    }

    private static void collectPropertyNames(JsonNode node, List<String> names) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                if ("properties".equals(field.getKey()) && field.getValue().isObject()) {
                    field.getValue().fieldNames().forEachRemaining(names::add);
                }
                collectPropertyNames(field.getValue(), names);
            });
            return;
        }

        if (node.isArray()) {
            node.forEach(child -> collectPropertyNames(child, names));
        }
    }

    private static List<String> fieldNames(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(element -> names.add(element.asText()));

        return names;
    }

    private static String taskBlock(String taskId) throws IOException {
        String tasks = Files.readString(TASKS_FILE);
        String[] blocks = tasks.split("(?m)^---$");

        for (String block : blocks) {
            if (block.contains("id: " + taskId + "\n")) {
                return block;
            }
        }

        throw new AssertionError("no task '" + taskId + "' in " + TASKS_FILE);
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
}
