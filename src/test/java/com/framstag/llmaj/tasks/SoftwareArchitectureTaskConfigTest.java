package com.framstag.llmaj.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.config.Config;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftwareArchitectureTaskConfigTest {

    private static final Path ANALYSIS_DIR = Path.of("analysis", "software-architecture");
    private static final Set<String> REMOVED_PER_MODULE_TASKS = Set.of(
            "ModuleAnalysisReports",
            "ModuleCyclomaticComplexityEvaluation",
            "ModuleVisibilityEvaluation",
            "ModuleInheritanceEvaluation",
            "ModuleMethodComplexityEvaluation",
            "ModuleNestingDepthEvaluation",
            "ModuleFieldVisibilityEvaluation",
            "ModuleClassCohesionEvaluation",
            "ModuleCouplingEvaluation",
            "ModuleTestCoverageEvaluation",
            "ModuleCircularDependencyEvaluation",
            "ModuleMethodCountEvaluation",
            "ModuleDocumentationRatioEvaluation",
            "ModuleDataClassEvaluation",
            "ModuleBooleanParameterEvaluation",
            "ModuleAnnotationEvaluation",
            "ModulePackageTangleEvaluation",
            "ModuleImportDiversityEvaluation"
    );

    @Test
    void analysisReadmePointsToTheAnalysisReference() throws Exception {
        String readme = Files.readString(ANALYSIS_DIR.resolve("README.md"));

        assertTrue(readme.contains("AnalysisReference.adoc.hbs"),
                "the directory listing names the reference document");
        assertTrue(readme.contains("## Analysis Methods and Interpretation"),
                "the README points at the reference as the place that describes an analysis");

        for (String budget : List.of(
                Config.DIAGRAM_MAX_NODES_PROPERTY,
                Config.DIAGRAM_MAX_OVERVIEW_NODES_PROPERTY,
                Config.DIAGRAM_MIN_EDGE_WEIGHT_PROPERTY,
                Config.DIAGRAM_MAX_EDGES_PER_NODE_PROPERTY,
                Config.DIAGRAM_MAX_GROUP_DIAGRAMS_PROPERTY,
                Config.DIAGRAM_MIN_CUT_NODE_LIMIT_PROPERTY,
                Config.GOD_CLASS_RANKING_LIMIT_PROPERTY,
                Config.GOD_CLASS_BATCH_RANKING_LIMIT_PROPERTY)) {
            assertFalse(readme.contains(budget),
                    "the README does not restate the budget " + budget + ", which the reference states");
        }

        assertFalse(readme.contains("## Dependency Diagrams"),
                "the diagram facts are described in the reference entry");
        assertFalse(readme.contains("## God Class Ranking"),
                "the ranking facts are described in the reference entry");
    }

    @Test
    void softwareArchitectureTasksUseBatchedJavaModuleEvaluation() throws Exception {
        String tasksYaml = Files.readString(ANALYSIS_DIR.resolve("tasks.yaml"));

        for (String removedTask : REMOVED_PER_MODULE_TASKS) {
            assertFalse(Pattern.compile("(?m)^id:\\s*" + Pattern.quote(removedTask) + "\\s*$")
                    .matcher(tasksYaml)
                    .find(), removedTask + " should be removed");
        }

        assertTrue(tasksYaml.contains("id: CollectJavaModuleReportsAll"));
        assertTrue(tasksYaml.contains("responseProperty: analysisReportsAll"));
        assertTrue(tasksYaml.contains("- module_analysis_reports_all"));
        assertTrue(tasksYaml.contains("id: InterModuleDependencyEvaluation"));
        assertTrue(tasksYaml.contains("  - module_analysis_reports_all\n"));
        assertFalse(tasksYaml.contains("  - module_analysis_reports\n"));
    }

    @Test
    void batchedTaskReferencesPointToExistingPromptsAndSchemas() throws Exception {
        String tasksYaml = Files.readString(ANALYSIS_DIR.resolve("tasks.yaml"));
        List<String> promptRefs = extractRefs(tasksYaml, "^prompt:\\s*(.+)$");
        List<String> schemaRefs = extractRefs(tasksYaml, "^responseFormat:\\s*(.+)$");

        for (String promptRef : promptRefs) {
            assertTrue(Files.exists(ANALYSIS_DIR.resolve(promptRef)), promptRef + " prompt missing");
        }
        for (String schemaRef : schemaRefs) {
            assertTrue(Files.exists(ANALYSIS_DIR.resolve(schemaRef)), schemaRef + " schema missing");
        }
    }

    @Test
    void batchedTasksActiveAndInterModuleDependencyUsesBatchReportTag() throws Exception {
        String tasksYaml = Files.readString(ANALYSIS_DIR.resolve("tasks.yaml"));

        assertRawReportCollectionTask(tasksYaml);

        for (String batchedTask : List.of(
                "CyclomaticComplexityEvaluationAll",
                "VisibilityEvaluationAll",
                "InheritanceEvaluationAll",
                "MethodComplexityEvaluationAll",
                "NestingDepthEvaluationAll",
                "FieldVisibilityEvaluationAll",
                "ClassCohesionEvaluationAll",
                "CouplingEvaluationAll",
                "TestCoverageEvaluationAll",
                "CircularDependencyEvaluationAll",
                "MethodCountEvaluationAll",
                "DocumentationRatioEvaluationAll",
                "DataClassEvaluationAll",
                "BooleanParameterEvaluationAll",
                "AnnotationEvaluationAll",
                "PackageTangleEvaluationAll",
                "ImportDiversityEvaluationAll",
                "DomainGlossary",
                "DomainVocabularyEvaluationAll")) {
            assertBatchTask(tasksYaml,
                    batchedTask,
                    decapitalize(batchedTask),
                    "module_analysis_reports_all");
        }

        String interModuleDependencyBlock = extractBlock(tasksYaml, "id: InterModuleDependencyEvaluation");
        assertTrue(interModuleDependencyBlock.contains("  - module_analysis_reports_all\n"));
        assertFalse(interModuleDependencyBlock.contains("  - module_analysis_reports\n"));
        assertFalse(tasksYaml.contains("  - module_analysis_reports\n"));
    }

    @Test
    void batchedSchemasParseAndExposeRequiredShapes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode moduleBatchEvaluation = mapper.readTree(Files.readString(ANALYSIS_DIR.resolve("results/ModuleBatchEvaluation.json")));
        assertEquals("object", moduleBatchEvaluation.path("type").asText());
        assertEquals(List.of("reasoning", "moduleEvaluations"), requiredFields(moduleBatchEvaluation));
        assertEquals("array", moduleBatchEvaluation
                .path("properties")
                .path("moduleEvaluations")
                .path("type")
                .asText());
        assertEquals(List.of("moduleName", "reasoning", "evaluations"), requiredFields(moduleBatchEvaluation
                .path("properties")
                .path("moduleEvaluations")
                .path("items")));

        JsonNode moduleAnalysisReportsAll = mapper.readTree(Files.readString(ANALYSIS_DIR.resolve("results/ModuleAnalysisReportsAll.json")));
        assertEquals("object", moduleAnalysisReportsAll.path("type").asText());
        assertEquals(List.of("reasoning", "reports", "skipped"), requiredFields(moduleAnalysisReportsAll));
        assertEquals("array", moduleAnalysisReportsAll
                .path("properties")
                .path("reports")
                .path("type")
                .asText());
        assertEquals(List.of("moduleName", "status", "programmingLanguage", "reportName", "reasoning"),
                requiredFields(moduleAnalysisReportsAll
                        .path("properties")
                        .path("reports")
                        .path("items")));
    }

    @Test
    void moduleSubdirectoriesPromptRequestsSingleCountCallWithAllWildcards() throws Exception {
        String prompt = Files.readString(ANALYSIS_DIR.resolve("prompts/module_subdirectories.md"));

        assertEquals(1, countOccurrences(prompt, "Do only one call to 'filesystem_count_per_filetype_and_directory', passing all wildcards in one go as an array."));
        assertTrue(prompt.contains("Do only one call to 'filesystem_count_per_filetype_and_directory', "
                + "passing all wildcards in one go as an array."));
    }

    @Test
    void legacyModuleAnalysisPromptMapsToolIdToReportDescriptorFields() throws Exception {
        String prompt = Files.readString(ANALYSIS_DIR.resolve("prompts/module_analysis_reports.md"));

        assertTrue(prompt.contains("`programmingLanguage`: `Java`"));
        assertTrue(prompt.contains("`reportName`: returned report id"));
        assertTrue(prompt.contains("`reasoning`: short note that raw Java report data was generated for this module"));
    }

    @Test
    void documentationUsesPromotedBatchedMetricSections() throws Exception {
        String documentation = Files.readString(ANALYSIS_DIR
                .resolve("documentation")
                .resolve("Documentation.adoc.hbs"));

        assertTrue(documentation.contains("=== Analysis of Cyclomatic Complexity per Module"));
        assertTrue(documentation.contains("{{#if cyclomaticComplexityEvaluationAll.moduleEvaluations}}"));
        assertTrue(documentation.contains("=== Batch Java Report Collection"));
        assertTrue(documentation.contains("{{#if analysisReportsAll.reports}}"));
        assertFalse(documentation.contains("### Batch Java Metric Evaluations"));
        assertFalse(documentation.contains("legacy raw report descriptors"));
        assertFalse(documentation.contains("{{#cyclomaticComplexityEvaluation.evaluations"));
        assertFalse(documentation.contains("{{#nestingDepthEvaluation.evaluations"));
    }

    @Test
    void theDomainLanguageTasksBuildTheGlossaryBeforeAnyModuleIsJudged() throws Exception {
        String tasksYaml = Files.readString(ANALYSIS_DIR.resolve("tasks.yaml"));

        String glossaryBlock = extractBlock(tasksYaml, "id: DomainGlossary");

        assertTrue(glossaryBlock.contains("toolWhitelist:\n  - java_get_all_domain_vocabulary_reports"),
                "the glossary reads the counted vocabulary and nothing else");
        assertFalse(glossaryBlock.contains("loopOn:"), "the glossary is built once for the project");

        String evaluationBlock = extractBlock(tasksYaml, "id: DomainVocabularyEvaluationAll");

        assertTrue(evaluationBlock.contains("  - domain_glossary\n"),
                "the module judgements read the glossary that fixes the meaning of a term");
        assertTrue(evaluationBlock.contains("prompt: prompts/domain_language_evaluation_all.md\n"),
                "the evaluation uses its own prompt");
        assertFalse(evaluationBlock.contains("loopOn:"), "the judgement is one batch call");
    }

    @Test
    void theGlossarySchemaExposesTheTermsWithTheirClassificationAndTheirModules() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode glossary = mapper.readTree(Files.readString(ANALYSIS_DIR.resolve("results/DomainGlossary.json")));

        assertEquals(List.of("reasoning", "terms"), requiredFields(glossary));
        assertEquals("array", glossary.path("properties").path("terms").path("type").asText());
        assertEquals(List.of("term", "classification", "meaning", "modules"),
                requiredFields(glossary.path("properties").path("terms").path("items")));
        assertEquals(List.of("domain", "technical", "generic"),
                toStringList(glossary.path("properties").path("terms").path("items")
                        .path("properties").path("classification").path("enum")));
        assertEquals(List.of("term", "meanings"),
                requiredFields(glossary.path("properties").path("ambiguousTerms").path("items")));
    }

    private List<String> toStringList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));

        return values;
    }

    private void assertRawReportCollectionTask(String tasksYaml) {
        String block = extractBlock(tasksYaml, "id: CollectJavaModuleReportsAll");

        assertTrue(block.contains("active: true"));
        assertTrue(block.contains("responseProperty: analysisReportsAll"));
        assertTrue(block.contains("  - modules\n"));
        assertTrue(block.contains("  - programming_language\n"));
        assertTrue(block.contains("  - module_subdirectories\n"));
        assertFalse(block.contains("  - module_analysis_reports\n"));
    }

    private void assertBatchTask(String tasksYaml, String taskId, String responseProperty, String dependencyTag) {
        String block = extractBlock(tasksYaml, "id: " + taskId);

        assertTrue(block.contains("active: true"), taskId + " should be active");
        assertTrue(block.contains("responseProperty: " + responseProperty), taskId + " responseProperty mismatch");
        assertTrue(block.contains("  - " + dependencyTag + "\n"), taskId + " dependency tag mismatch");
        assertFalse(block.contains("  - module_analysis_reports\n"), taskId + " must not depend on legacy tag");
    }

    @Test
    void theContextMapTaskReadsTheOverviewOfTheWholeProject() throws Exception {
        String tasksYaml = Files.readString(ANALYSIS_DIR.resolve("tasks.yaml"));
        String block = extractBlock(tasksYaml, "id: ContextMapEvaluation");

        assertTrue(block.contains("active: true"));
        assertTrue(block.contains("responseProperty: contextMap"));
        assertTrue(block.contains("toolWhitelist:\n  - java_get_domain_language_overview"),
                "the context map reads the project-level overview and nothing else");
        assertTrue(block.contains("  - domain_vocabulary_evaluation\n"),
                "the context map stands on the counted vocabulary");
        assertTrue(block.contains("  - inter_module_dependency_evaluation\n"),
                "the structural side of the quadrant comes from the import matrix");
        assertFalse(block.contains("loopOn:"), "the context map is one call for the project");
    }

    @Test
    void theContextMapSchemaExposesContextsRelationsAndUnplacedModules() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode contextMap = mapper.readTree(Files.readString(ANALYSIS_DIR.resolve("results/ContextMap.json")));

        assertEquals("object", contextMap.path("type").asText());
        assertEquals(List.of("reasoning", "contexts"), requiredFields(contextMap));
        assertEquals(List.of("name", "modules", "language", "reasoning"),
                requiredFields(contextMap.path("properties").path("contexts").path("items")));
        assertEquals(List.of("fromContext", "toContext", "relation", "evidence", "sharedLanguage",
                        "structurallyCoupled"),
                requiredFields(contextMap.path("properties").path("relations").path("items")));
        assertEquals(List.of("module", "reason"),
                requiredFields(contextMap.path("properties").path("unplacedModules").path("items")));
    }

    private String extractBlock(String yaml, String marker) {
        int start = yaml.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException(marker + " not found");
        }
        int nextSeparator = yaml.indexOf("\n---", start + marker.length());
        return yaml.substring(start, nextSeparator < 0 ? yaml.length() : nextSeparator);
    }

    private List<String> requiredFields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.path("required").forEach(field -> fields.add(field.asText()));
        return fields;
    }

    private int countOccurrences(String text, String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private String decapitalize(String value) {
        return value.substring(0, 1).toLowerCase() + value.substring(1);
    }

    private List<String> extractRefs(String yaml, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE)
                .matcher(yaml)
                .results()
                .map(match -> match.group(1).trim())
                .toList();
    }
}
