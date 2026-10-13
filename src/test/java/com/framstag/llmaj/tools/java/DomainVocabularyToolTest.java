package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.tools.java.language.DomainLanguageSettings;
import com.framstag.llmaj.tools.java.language.DomainLexicon;
import com.framstag.llmaj.tools.java.language.LexiconTier;
import com.framstag.llmaj.tools.java.language.ModuleLanguageEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainVocabularyToolTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    /**
     * A module report with one production type whose name, method, field and documentation carry a known
     * vocabulary, written to disk the way the report collection writes it.
     */
    private void writeModuleReport(String moduleName, String typeName, String methodName, int repeats)
            throws IOException {
        ObjectNode clazz = objectMapper.createObjectNode();
        clazz.put("qualifiedName", typeName);
        clazz.put("documentation", "The realm cache holds the realm entries");

        ArrayNode methods = clazz.putArray("methods");
        for (int i = 0; i < repeats; i++) {
            ObjectNode method = methods.addObject();
            method.put("name", methodName);
            method.put("descriptor", "()V");
        }

        ArrayNode fields = clazz.putArray("fields");
        ObjectNode field = fields.addObject();
        field.put("name", "realmStore");
        field.put("type", "com.example.core" + ".RealmStore");

        ArrayNode clazzes = objectMapper.createArrayNode();
        clazzes.add(clazz);

        ObjectNode buildUnit = objectMapper.createObjectNode();
        buildUnit.put("name", typeName);
        buildUnit.put("production", true);
        buildUnit.put("generated", false);
        buildUnit.set("imports", objectMapper.createArrayNode());
        buildUnit.set("references", objectMapper.createArrayNode());
        buildUnit.set("classes", clazzes);

        ObjectNode packageNode = objectMapper.createObjectNode();
        packageNode.put("name", "com.example.core");
        packageNode.set("buildUnits", objectMapper.createArrayNode().add(buildUnit));

        ObjectNode module = objectMapper.createObjectNode();
        module.put("name", moduleName);
        module.put("reportFormatVersion", Module.CURRENT_REPORT_FORMAT_VERSION);
        module.set("packages", objectMapper.createArrayNode().add(packageNode));

        Path reportDirectory = tempDir.resolve("Java");
        Files.createDirectories(reportDirectory);
        objectMapper.writeValue(reportDirectory.resolve("Java_" + moduleName + ".json").toFile(), module);
    }

    private AnalysisContext contextWithModules(String... moduleNames) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : moduleNames) {
            ObjectNode module = modules.addObject();
            module.put("name", moduleName);
            module.put("path", ".");
            module.putObject("purpose").put("purpose", "The realm cache resolves the artifact");

            ArrayNode evaluations = module.putObject("architecture").putArray("architectureElements");
            evaluations.addObject().put("finding", "The cache is resolved from the repository");

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            languages.addObject().put("name", "Java").put("version", "unknown");
        }

        return new AnalysisContext(tempDir, tempDir,
                Map.of(Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY,
                        tempDir.resolve("domain_lexicon.csv").toString()),
                state);
    }

    private DomainLexicon writeLexicon(String... termAndTier) throws IOException {
        List<String> lines = new ArrayList<>(List.of("Version,test", "Term,Tier,Note"));

        for (int i = 0; i + 1 < termAndTier.length; i += 2) {
            lines.add(termAndTier[i] + "," + termAndTier[i + 1] + ",test entry");
        }

        Path path = tempDir.resolve("domain_lexicon.csv");
        Files.write(path, lines);

        return DomainLexicon.load(path);
    }

    private DomainLanguageSettings settings(int minimumCandidateTerms, int topTermsPerModule) {
        return new DomainLanguageSettings(tempDir.resolve("domain_lexicon.csv"), minimumCandidateTerms,
                topTermsPerModule);
    }

    private List<String> readCsv(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    @Test
    void theProviderWritesTheThreeEvidenceFilesAndReturnsTheEvidence() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 3);
        DomainLexicon lexicon = writeLexicon("the", "noise", "of", "noise", "is", "noise", "from", "noise");

        ModuleLanguageEvidence evidence = new JavaTool(contextWithModules("core"))
                .getDomainVocabularyReport("core", settings(1, 20), lexicon);

        assertEquals("core", evidence.moduleName());
        assertTrue(evidence.sufficientEvidence());
        assertTrue(evidence.shares().domainOccurrences() > 0);
        assertTrue(evidence.candidateTerms() > 0);

        Path terms = tempDir.resolve("DomainVocabulary/core.csv");
        Path metrics = tempDir.resolve("DomainLanguageMetrics/core.csv");
        Path cohesion = tempDir.resolve("NamingCohesion/core.csv");

        assertTrue(Files.exists(terms));
        assertTrue(Files.exists(metrics));
        assertFalse(Files.exists(cohesion), "a single build unit has no pair, so no pair file is written");

        List<String> termRows = readCsv(terms);

        assertEquals("Term,Tier,Occurrences,TypeOccurrences,MethodOccurrences,MethodVerbOccurrences,"
                + "FieldOccurrences,PackageOccurrences,DocOccurrences", termRows.get(0));
        assertTrue(termRows.stream().anyMatch(row -> row.startsWith("realm,")),
                "the domain term of the module is part of the evidence");
    }

    @Test
    void theTermEvidenceKeepsTheTermsTheMetricsDrop() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 1);
        DomainLexicon lexicon = writeLexicon("cache", "technical");

        new JavaTool(contextWithModules("core")).getDomainVocabularyReport("core", settings(1, 20), lexicon);

        List<String> termRows = readCsv(tempDir.resolve("DomainVocabulary/core.csv"));

        assertTrue(termRows.stream().anyMatch(row -> row.startsWith("cache,technical,")),
                "a technical term stays in the evidence with its tier");
    }

    @Test
    void twoRunsOverTheSameReportProduceIdenticalEvidence() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 2);
        DomainLexicon lexicon = writeLexicon("the", "noise");

        JavaTool javaTool = new JavaTool(contextWithModules("core"));
        javaTool.getDomainVocabularyReport("core", settings(1, 20), lexicon);

        List<String> firstRun = readCsv(tempDir.resolve("DomainVocabulary/core.csv"));
        List<String> firstMetrics = readCsv(tempDir.resolve("DomainLanguageMetrics/core.csv"));

        javaTool.getDomainVocabularyReport("core", settings(1, 20), lexicon);

        assertEquals(firstRun, readCsv(tempDir.resolve("DomainVocabulary/core.csv")));
        assertEquals(firstMetrics, readCsv(tempDir.resolve("DomainLanguageMetrics/core.csv")));
    }

    @Test
    void aModuleBelowTheMinimumCarriesNoMetricValue() throws IOException {
        writeModuleReport("small", "com.example.core.RealmCache", "resolveRealm", 1);
        DomainLexicon lexicon = writeLexicon();

        ModuleLanguageEvidence evidence = new JavaTool(contextWithModules("small"))
                .getDomainVocabularyReport("small", settings(500, 20), lexicon);

        assertFalse(evidence.sufficientEvidence());
        assertEquals(500, evidence.minimumCandidateTerms());
        assertNull(evidence.alignment().purposeCoverage());
        assertNull(evidence.cohesion().value());

        Map<String, Object> result = new JavaTool(contextWithModules("small")).getAllDomainVocabularyReports();
        Map<?, ?> descriptor = (Map<?, ?>) ((List<?>) result.get("reports")).get(0);

        assertEquals(Boolean.FALSE, descriptor.get("sufficientEvidence"));
        assertTrue(descriptor.containsKey("leadingTerms"),
                "a module below the gate still names the terms it has");
        assertFalse(descriptor.containsKey("technicalShare"),
                "a module below the gate carries no share");
        assertFalse(descriptor.containsKey("namingCohesion"));
        assertFalse(descriptor.containsKey("purposeCoverage"));

        List<String> metrics = readCsv(tempDir.resolve("DomainLanguageMetrics/small.csv"));

        assertTrue(metrics.stream().anyMatch(row -> row.equals("sufficientEvidence,false")));
        assertFalse(metrics.stream().anyMatch(row -> row.startsWith("technicalShare,")),
                "a module below the minimum carries no share");
    }

    @Test
    void theBatchToolAnswersForEveryModuleAndNamesWhatItSkipped() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 2);

        ObjectNode state = contextWithModules("core").getAnalysisState();
        ArrayNode modules = (ArrayNode) state.get("modules").get("modules");
        ObjectNode notJava = modules.addObject();
        notJava.put("name", "docs");
        notJava.putObject("programmingLanguages").putArray("programmingLanguages")
                .addObject().put("name", "Python");
        ObjectNode missing = modules.addObject();
        missing.put("name", "missing");
        missing.putObject("programmingLanguages").putArray("programmingLanguages")
                .addObject().put("name", "Java");

        AnalysisContext context = new AnalysisContext(tempDir, tempDir,
                Map.of(Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY,
                        tempDir.resolve("domain_lexicon.csv").toString()), state);
        writeLexicon("the", "noise");

        Map<String, Object> result = new JavaTool(context).getAllDomainVocabularyReports();

        assertEquals("Domain vocabulary", result.get("metric"));
        assertEquals(1, ((List<?>) result.get("reports")).size());
        assertEquals(2, ((List<?>) result.get("skipped")).size());

        String skipped = result.get("skipped").toString();

        assertTrue(skipped.contains("docs"), skipped);
        assertTrue(skipped.contains("not detected as Java"), skipped);
        assertTrue(skipped.contains("missing"), skipped);
        assertTrue(skipped.contains("No raw Java module report"), skipped);
    }

    @Test
    void theBatchToolReportsAnUnusableLexiconInsteadOfAnEmptyResult() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 1);

        // Deliberately no lexicon file at the configured path.
        Map<String, Object> result = new JavaTool(contextWithModules("core")).getAllDomainVocabularyReports();

        assertEquals("ERROR", ((Map<?, ?>) result.get("lexicon")).get("status"));
        assertEquals(0, ((List<?>) result.get("reports")).size());
        assertEquals(1, ((List<?>) result.get("skipped")).size());
        assertTrue(result.get("skipped").toString().contains("domain lexicon"), result.toString());
    }

    @Test
    void anOutdatedReportIsSkippedInsteadOfMeasured() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 1);

        Path report = tempDir.resolve("Java/Java_core.json");
        ObjectNode module = (ObjectNode) objectMapper.readTree(report.toFile());
        module.put("reportFormatVersion", 1);
        objectMapper.writeValue(report.toFile(), module);

        writeLexicon("the", "noise");

        Map<String, Object> result = new JavaTool(contextWithModules("core")).getAllDomainVocabularyReports();

        assertEquals(0, ((List<?>) result.get("reports")).size());
        assertTrue(result.get("skipped").toString().contains("format version"), result.toString());
    }

    @Test
    void theLeadingTermsOfTheResultAreBoundedWhileTheEvidenceKeepsEveryTerm() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 2);
        DomainLexicon lexicon = writeLexicon("the", "noise");

        ModuleLanguageEvidence evidence = new JavaTool(contextWithModules("core"))
                .getDomainVocabularyReport("core", settings(1, 2), lexicon);

        assertEquals(2, evidence.leadingTerms(2).size());
        assertTrue(evidence.terms().size() > 2, "the evidence keeps every term");
        assertTrue(readCsv(tempDir.resolve("DomainVocabulary/core.csv")).size() > 3,
                "the CSV carries a header and every term, not only the leading ones");
    }

    @Test
    void theDefaultSettingsComeFromTheConfigurationAndFallBackToTheSeededValues() {
        DomainLanguageSettings defaults = DomainLanguageSettings.from(Map.of(), tempDir);

        assertEquals(DomainLanguageSettings.DEFAULT_MINIMUM_CANDIDATE_TERMS, defaults.minimumCandidateTerms());
        assertEquals(DomainLanguageSettings.DEFAULT_TOP_TERMS, defaults.topTermsPerModule());
        assertEquals(tempDir.resolve(DomainLanguageSettings.DEFAULT_LEXICON_PATH), defaults.lexiconPath());

        DomainLanguageSettings configured = DomainLanguageSettings.from(Map.of(
                Config.DOMAIN_LANGUAGE_MINIMUM_CANDIDATE_TERMS_PROPERTY, "17",
                Config.DOMAIN_LANGUAGE_TOP_TERMS_PROPERTY, "3",
                Config.DOMAIN_LANGUAGE_LEXICON_PROPERTY, "analysis/own_lexicon.csv"), tempDir);

        assertEquals(17, configured.minimumCandidateTerms());
        assertEquals(3, configured.topTermsPerModule());
        assertEquals(tempDir.resolve("analysis/own_lexicon.csv"), configured.lexiconPath());
    }

    @Test
    void theVocabularyOfAModuleIsComputedWithoutReadingItsSources() throws IOException {
        writeModuleReport("core", "com.example.core.RealmCache", "resolveRealm", 1);
        DomainLexicon lexicon = writeLexicon();

        ModuleLanguageEvidence evidence = new JavaTool(contextWithModules("core"))
                .getDomainVocabularyReport("core", settings(1, 20), lexicon);

        assertEquals(LexiconTier.UNCLASSIFIED, evidence.terms().stream()
                .filter(term -> term.term().equals("realm"))
                .findFirst()
                .orElseThrow()
                .tier());
        assertNotNull(evidence.lexiconVersion());
    }
}
