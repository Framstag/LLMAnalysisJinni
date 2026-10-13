package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.java.language.overview.ContextMapReports;
import com.framstag.llmaj.tools.java.language.overview.ImportMatrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMapToolTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private void writeLexicon(String... termAndTier) throws IOException {
        List<String> lines = new java.util.ArrayList<>(List.of("Version,test", "Term,Tier,Note"));

        for (int i = 0; i + 1 < termAndTier.length; i += 2) {
            lines.add(termAndTier[i] + "," + termAndTier[i + 1] + ",test entry");
        }

        Files.write(tempDir.resolve("domain_lexicon.csv"), lines);
    }

    private void writeModuleReport(String moduleName, String... typeNames) throws IOException {
        ArrayNode buildUnits = objectMapper.createArrayNode();

        for (String typeName : typeNames) {
            ObjectNode clazz = objectMapper.createObjectNode();
            clazz.put("qualifiedName", "x." + typeName);
            clazz.set("methods", objectMapper.createArrayNode());
            clazz.set("fields", objectMapper.createArrayNode());

            ObjectNode buildUnit = objectMapper.createObjectNode();
            buildUnit.put("name", "x." + typeName);
            buildUnit.put("production", true);
            buildUnit.put("generated", false);
            buildUnit.set("imports", objectMapper.createArrayNode());
            buildUnit.set("references", objectMapper.createArrayNode());
            buildUnit.set("classes", objectMapper.createArrayNode().add(clazz));
            buildUnits.add(buildUnit);
        }

        ObjectNode packageNode = objectMapper.createObjectNode();
        packageNode.put("name", "x");
        packageNode.set("buildUnits", buildUnits);

        ObjectNode module = objectMapper.createObjectNode();
        module.put("name", moduleName);
        module.put("reportFormatVersion", Module.CURRENT_REPORT_FORMAT_VERSION);
        module.set("packages", objectMapper.createArrayNode().add(packageNode));

        Path reportDirectory = tempDir.resolve("Java");
        Files.createDirectories(reportDirectory);
        objectMapper.writeValue(reportDirectory.resolve("Java_" + moduleName + ".json").toFile(), module);
    }

    private void writeImportMatrix(String... rows) throws IOException {
        Path directory = tempDir.resolve(ImportMatrix.REPORT_DIRECTORY);
        Files.createDirectories(directory);

        List<String> lines = new java.util.ArrayList<>(List.of("From,To,ImportCount"));
        lines.addAll(List.of(rows));

        Files.write(directory.resolve(ImportMatrix.REPORT_DIRECTORY + ".csv"), lines, StandardCharsets.UTF_8);
    }

    private AnalysisContext contextOf(String... moduleNames) {
        return contextOfWithShare("1.0", moduleNames);
    }

    /**
     * A share of 1.0 sets nothing aside, which is what the tests of the quadrant mechanics want: their fixtures
     * are too small to have a vocabulary of their own.
     */
    private AnalysisContext contextOfWithShare(String ubiquitousModuleShare, String... moduleNames) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : moduleNames) {
            ObjectNode module = modules.addObject();
            module.put("name", moduleName);
            module.put("path", ".");
            module.putObject("purpose").put("purpose", "The realm of " + moduleName);

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            languages.addObject().put("name", "Java").put("version", "unknown");
        }

        return new AnalysisContext(tempDir, tempDir,
                Map.of(Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY,
                        tempDir.resolve("domain_lexicon.csv").toString(),
                        Config.CONTEXT_MAP_LEADING_TERMS_PROPERTY, "20",
                        Config.CONTEXT_MAP_MAX_PAIRS_PROPERTY, "25",
                        Config.CONTEXT_MAP_UBIQUITOUS_SHARE_PROPERTY, ubiquitousModuleShare),
                state);
    }

    @Test
    void theOverviewAnswersForTheWholeProjectInOneCall() throws IOException {
        writeLexicon();
        writeModuleReport("api", "RealmCache");
        writeModuleReport("core", "RealmArtifact");
        writeImportMatrix("api,core,5");

        Map<String, Object> result = new JavaTool(contextOf("api", "core")).getDomainLanguageOverview();

        assertEquals("Domain language overview", result.get("metric"));
        assertEquals(2, result.get("moduleCount"));
        assertEquals("OK", ((Map<?, ?>) result.get("importMatrix")).get("status"));
        assertTrue(result.get("termSpan").toString().contains("realm"), result.toString());
        assertFalse(((List<?>) result.get("pairs")).isEmpty());
        assertEquals(0, ((List<?>) result.get("skipped")).size());
    }

    @Test
    void theQuadrantOfTheProjectIsCounted() throws IOException {
        writeLexicon();
        writeModuleReport("api", "RealmCache");
        writeModuleReport("core", "RealmArtifact");
        writeModuleReport("web", "ArtifactPage");
        writeImportMatrix("api,core,5");

        Map<String, Object> result = new JavaTool(contextOf("api", "core", "web")).getDomainLanguageOverview();
        Map<?, ?> counts = (Map<?, ?>) result.get("classificationCounts");

        assertEquals(5, counts.size(), "four classifications plus the case of missing evidence");
        assertEquals(1, counts.get("coupled"), "api and core share realm and import each other");
        assertTrue((Integer) counts.get("candidate domain leak") >= 1,
                "web shares a term with a module it does not import: " + counts);
    }

    @Test
    void aProjectWithoutAnImportMatrixSaysSoInsteadOfClaimingZeroImports() throws IOException {
        writeLexicon();
        writeModuleReport("api", "RealmCache");
        writeModuleReport("core", "RealmArtifact");

        Map<String, Object> result = new JavaTool(contextOf("api", "core")).getDomainLanguageOverview();

        assertEquals("MISSING", ((Map<?, ?>) result.get("importMatrix")).get("status"));
        assertEquals(1, ((Map<?, ?>) result.get("classificationCounts")).get("structural evidence missing"));
        assertEquals(0, ((Map<?, ?>) result.get("classificationCounts")).get("candidate domain leak"),
                "a missing matrix is not a leak of every pair");
    }

    @Test
    void aSingleModuleProjectIsAnsweredWithoutPairs() throws IOException {
        writeLexicon();
        writeModuleReport("only", "RealmCache");

        Map<String, Object> result = new JavaTool(contextOf("only")).getDomainLanguageOverview();

        assertEquals(1, result.get("moduleCount"));
        assertEquals(List.of(), result.get("pairs"));
        assertEquals(0, ((Map<?, ?>) result.get("classificationCounts")).get("coupled"));
    }

    @Test
    void theEvidenceKeepsEveryPairWhileTheResultIsBounded() throws IOException {
        writeLexicon();

        String[] moduleNames = {"alpha", "bravo", "charlie", "delta", "echo"};

        for (String moduleName : moduleNames) {
            writeModuleReport(moduleName, "RealmArtifact", "ArtifactRealm", "RealmPage");
        }

        writeImportMatrix("alpha,bravo,1");

        AnalysisContext context = new AnalysisContext(tempDir, tempDir,
                Map.of(Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY,
                        tempDir.resolve("domain_lexicon.csv").toString(),
                        Config.CONTEXT_MAP_LEADING_TERMS_PROPERTY, "20",
                        Config.CONTEXT_MAP_MAX_PAIRS_PROPERTY, "2"),
                contextOf(moduleNames).getAnalysisState());

        Map<String, Object> result = new JavaTool(context).getDomainLanguageOverview();

        // Ten pairs of five modules, and at most two pairs per classification in the result.
        assertEquals(10, Files.readAllLines(tempDir.resolve(ContextMapReports.REPORT_TYPE)
                        .resolve("StructuralVsLexical.csv"))
                .size() - 1);
        assertTrue(((List<?>) result.get("pairs")).size() <= 5 * 2,
                "the result carries the bounded list, not all ten pairs");
    }

    @Test
    void aTermMostModulesUseIsSetAsideAndReportedAsItsOwnView() throws IOException {
        writeLexicon();
        // Four of five modules carry the project's own name; the fifth has a vocabulary of its own.
        writeModuleReport("alpha", "MavenRealm");
        writeModuleReport("bravo", "MavenRealm");
        writeModuleReport("charlie", "MavenRealm");
        writeModuleReport("delta", "MavenRealm");
        writeModuleReport("echo", "ArtifactPage");

        Map<String, Object> result = new JavaTool(contextOfWithShare("0.5", "alpha", "bravo", "charlie",
                "delta", "echo")).getDomainLanguageOverview();

        assertEquals(2, result.get("ubiquitousTermCount"),
                "the project's own name and the one word all four modules repeat are set aside");
        assertTrue(result.get("ubiquitousTerms").toString().contains("maven"), result.toString());
        assertTrue(result.get("ubiquitousTerms").toString().contains("realm"), result.toString());

        List<String> ubiquitous = Files.readAllLines(tempDir.resolve(ContextMapReports.REPORT_TYPE)
                .resolve("UbiquitousTerms.csv"));

        assertEquals("Term,Occurrences,ModuleCount,Modules,DominantTier", ubiquitous.getFirst());
        assertTrue(ubiquitous.get(1).startsWith("maven,4,4,"), ubiquitous.toString());

        // With the project's own name set aside, the four modules that carry it share no language at all.
        assertEquals(0, ((Map<?, ?>) result.get("classificationCounts")).get("candidate domain leak"),
                "the name of the project marks no boundary: " + result.get("classificationCounts"));
    }

    @Test
    void aProjectWideTermEstablishesNoOwnership() throws IOException {
        writeLexicon();
        writeModuleReport("alpha", "MavenRealm");
        writeModuleReport("bravo", "MavenRealm");
        writeModuleReport("charlie", "MavenRealm");
        writeModuleReport("delta", "MavenRealm");

        Map<String, Object> result = new JavaTool(contextOfWithShare("0.5", "alpha", "bravo", "charlie",
                "delta")).getDomainLanguageOverview();

        assertEquals(List.of(), result.get("intrusions"),
                "the project's own name is owned by nobody, so nobody borrows it");
        assertTrue(((List<?>) result.get("termSpan")).isEmpty(),
                "and it holds no glossary candidate either");
    }

    @Test
    void aModuleWithoutAReportIsNamedAsSkippedAndKeptOutOfTheEvidence() throws IOException {
        writeLexicon();
        writeModuleReport("api", "RealmCache");

        Map<String, Object> result = new JavaTool(contextOf("api", "missing")).getDomainLanguageOverview();

        assertEquals(1, result.get("moduleCount"));
        assertEquals(1, ((List<?>) result.get("skipped")).size());
        assertTrue(result.get("skipped").toString().contains("missing"), result.toString());
    }

    @Test
    void anUnusableLexiconIsReportedInsteadOfAnEmptyOverview() throws IOException {
        writeModuleReport("api", "RealmCache");

        Map<String, Object> result = new JavaTool(contextOf("api")).getDomainLanguageOverview();

        assertEquals("ERROR", ((Map<?, ?>) result.get("lexicon")).get("status"));
        assertEquals(null, result.get("moduleCount"));
        assertEquals(1, ((List<?>) result.get("skipped")).size());
    }
}
