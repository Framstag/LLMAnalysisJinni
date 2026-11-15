package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consolidated namespace evidence is the input of the classification of the technology of a project that holds no
 * dependency data, so it has to be small, deterministic and clean. It carries namespaces and counts and nothing else,
 * it drops what every Java project imports and what the project imports from itself, and it states the reason when it
 * is empty instead of returning a bare empty list.
 */
class JavaToolImportTechnologyEvidenceTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    private static BuildUnit buildUnit(String name, boolean production, List<String> imports) {
        return new BuildUnit(name, production, false, imports, List.of(),
                List.of(new Clazz(name + "Type", null)));
    }

    private static Module module(String moduleName, String packageName, BuildUnit... buildUnits) {
        Package pck = new Package(packageName);

        for (BuildUnit buildUnit : buildUnits) {
            pck.addBuildUnit(buildUnit);
        }

        Module module = new Module(moduleName);
        module.addPackage(pck);

        return module;
    }

    private void writeReport(String moduleName, Module module) throws IOException {
        Path reportDirectory = tempDir.resolve("Java");
        Files.createDirectories(reportDirectory);
        objectMapper.writeValue(reportDirectory.resolve("Java_" + moduleName + ".json").toFile(), module);
    }

    private AnalysisContext contextOf(Map<String, String> properties, String... moduleNames) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        for (String moduleName : moduleNames) {
            ObjectNode module = modules.addObject();
            module.put("name", moduleName);
            module.put("path", ".");

            ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
            languages.addObject().put("name", "Java").put("version", "unknown");
        }

        return new AnalysisContext(tempDir, tempDir, properties, state);
    }

    private Map<String, Object> evidenceOf(Map<String, String> properties,
                                           Map<String, Module> modules) throws IOException {
        for (Map.Entry<String, Module> entry : modules.entrySet()) {
            writeReport(entry.getKey(), entry.getValue());
        }

        AnalysisContext context = contextOf(properties, modules.keySet().toArray(String[]::new));

        return new JavaTool(context).getImportTechnologyEvidence();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groupsOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("groups");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> prefixesOf(Map<String, Object> group) {
        return (List<Map<String, Object>>) group.get("prefixes");
    }

    private static Map<String, Object> group(Map<String, Object> result, String root) {
        return groupsOf(result).stream()
                .filter(group -> root.equals(group.get("root")))
                .findFirst()
                .orElse(null);
    }

    private static List<String> rootsOf(Map<String, Object> result) {
        return groupsOf(result).stream().map(group -> (String) group.get("root")).toList();
    }

    private static List<String> prefixesNamed(Map<String, Object> result) {
        return groupsOf(result).stream()
                .flatMap(group -> prefixesOf(group).stream())
                .map(prefix -> (String) prefix.get("prefix"))
                .toList();
    }

    @Test
    void onlyProductionCodeIsEvidence() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of("core", module("core", "demo.core",
                buildUnit("demo.core.Service", true, List.of("org.slf4j.Logger")),
                buildUnit("demo.core.ServiceTest", false, List.of("org.junit.jupiter.api.Test")))));

        assertTrue(rootsOf(result).contains("org"), rootsOf(result).toString());
        assertTrue(prefixesNamed(result).contains("org.slf4j"), prefixesNamed(result).toString());
        assertFalse(prefixesNamed(result).contains("org.junit.jupiter"),
                "a namespace only the test code imports is no evidence: " + prefixesNamed(result));
    }

    @Test
    void aPrefixImportedByTwoModulesIsSummedIntoOneEntry() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of(
                "core", module("core", "demo.core", buildUnit("demo.core.Service", true,
                        List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"))),
                "web", module("web", "demo.web", buildUnit("demo.web.Controller", true,
                        List.of("org.slf4j.Logger")))));

        Map<String, Object> org = group(result, "org");

        assertEquals(1, prefixesOf(org).size(), prefixesOf(org).toString());
        assertEquals(3, prefixesOf(org).stream()
                        .filter(prefix -> "org.slf4j".equals(prefix.get("prefix")))
                        .findFirst()
                        .orElseThrow()
                        .get("importCount"),
                "the imports of both modules are summed into one entry");
        assertEquals(3, org.get("importCount"));
    }

    @Test
    void thePlatformNamespacesAreNotEvidence() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of("core", module("core", "demo.core",
                buildUnit("demo.core.Service", true, List.of(
                        "java.util.List",
                        "jdk.internal.misc.Unsafe",
                        "sun.misc.Unsafe",
                        "com.sun.net.httpserver.HttpServer")))));

        assertTrue(groupsOf(result).isEmpty(), rootsOf(result).toString());
        assertFalse(rootsOf(result).contains("com"),
                "com.sun is a platform namespace and not evidence of the project");
    }

    @Test
    void theJakartaNamespacesAndJavaFxAreEvidence() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of("core", module("core", "demo.core",
                buildUnit("demo.core.Service", true, List.of(
                        "javax.persistence.Entity",
                        "jakarta.ws.rs.GET",
                        "javafx.application.Application")))));

        assertEquals(List.of("jakarta", "javafx", "javax"), rootsOf(result).stream().sorted().toList(),
                "javax and jakarta hold the legacy markers, and javafx is not the java root" + rootsOf(result));
        assertTrue(prefixesNamed(result).contains("javax.persistence"), prefixesNamed(result).toString());
    }

    @Test
    void anEntryThatNamesATypeFoldsIntoItsRootGroup() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of("core", module("core", "demo.core",
                buildUnit("demo.core.Cli", true, List.of("picocli.CommandLine", "org.slf4j.Logger")))));

        Map<String, Object> picocli = group(result, "picocli");

        assertTrue(picocli != null, "a library used through one class is still evidence: " + rootsOf(result));
        assertEquals(1, picocli.get("importCount"));
        assertEquals(0, picocli.get("totalPrefixes"),
                "the type name is not reported as a namespace: " + prefixesNamed(result));
        assertTrue(prefixesNamed(result).contains("org.slf4j"),
                "a lower case second segment stays a namespace: " + prefixesNamed(result));
    }

    @Test
    void anArrayDescriptorIsNotANamespace() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of("core", module("core", "demo.core",
                buildUnit("demo.core.Service", true, List.of(
                        "[Lorg.example.Foo",
                        ".byte[]",
                        ".String[]",
                        "org.slf4j.Logger")))));

        assertTrue(rootsOf(result).stream().noneMatch(root -> root.startsWith("[")), rootsOf(result).toString());
        assertTrue(rootsOf(result).stream().noneMatch(root -> root.isBlank()),
                "a type descriptor of the platform leaves no group without a root: " + rootsOf(result));
        assertEquals(List.of("org"), rootsOf(result));
    }

    @Test
    void everyNamespaceOfTheProjectIsRemoved() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of(
                "core", module("core", "demo.core", buildUnit("demo.core.Service", true,
                        List.of("demo.shared.Api", "org.slf4j.Logger"))),
                "shared", module("shared", "demo.shared", buildUnit("demo.shared.Api", true, List.of()))));

        assertFalse(rootsOf(result).contains("demo"),
                "demo.shared is the namespace of a module of the project, so it is no evidence: "
                        + rootsOf(result));
        assertEquals(List.of("org"), rootsOf(result));
    }

    @Test
    void theEvidenceIsBoundedAndStatesTheTotal() throws Exception {
        Module module = module("core", "demo.core", buildUnit("demo.core.Service", true, List.of(
                "org.slf4j.Logger",
                "org.apache.commons.lang3.StringUtils",
                "com.fasterxml.jackson.databind.ObjectMapper",
                "io.github.some.Library")));

        Map<String, Object> bounded = evidenceOf(Map.of(
                Config.TECHNOLOGY_EVIDENCE_GROUPS_PROPERTY, "2",
                Config.TECHNOLOGY_EVIDENCE_PREFIXES_PER_GROUP_PROPERTY, "1"), Map.of("core", module));

        assertEquals(3, bounded.get("totalGroups"));
        assertEquals(2, bounded.get("returnedGroups"));
        assertEquals(2, groupsOf(bounded).size());
        assertEquals(2, bounded.get("groupsLimit"));
        assertEquals(1, bounded.get("prefixesPerGroupLimit"));

        Map<String, Object> unbounded = evidenceOf(Map.of(), Map.of("core", module));

        assertEquals(3, unbounded.get("returnedGroups"),
                "a workspace that names no bound is bounded by the seeded value");
        assertEquals(Config.getBudget(Map.of(), Config.TECHNOLOGY_EVIDENCE_GROUPS_PROPERTY,
                JavaTool.DEFAULT_TECHNOLOGY_EVIDENCE_GROUPS), unbounded.get("groupsLimit"));
        assertEquals(2, group(unbounded, "org").get("totalPrefixes"));
        assertEquals(2, prefixesOf(group(unbounded, "org")).size(),
                "the seeded prefixes per group shows every prefix of an ordinary group");
    }

    @Test
    void aProjectThatImportsNothingExternalStatesTheReason() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of("core", module("core", "demo.core",
                buildUnit("demo.core.Service", true, List.of("java.util.List", "demo.core.Model")))));

        assertTrue(groupsOf(result).isEmpty());
        assertTrue(((String) result.get("reason")).contains("imports no namespace"),
                (String) result.get("reason"));
    }

    @Test
    void aProjectWithoutAParsedModuleStatesTheReason() throws Exception {
        AnalysisContext context = contextOf(Map.of());

        Map<String, Object> result = new JavaTool(context).getImportTechnologyEvidence();

        assertTrue(groupsOf(result).isEmpty());
        assertTrue(((String) result.get("reason")).contains("No module of the project holds a parsed Java report"),
                (String) result.get("reason"));
    }

    @Test
    void theEvidenceCarriesNoArtifactAndNoVersion() throws Exception {
        Map<String, Object> result = evidenceOf(Map.of(), Map.of("core", module("core", "demo.core",
                buildUnit("demo.core.Service", true, List.of("org.springframework.context.ApplicationContext")))));

        assertEquals(List.of("groups", "returnedGroups", "totalGroups", "groupsLimit", "prefixesPerGroupLimit",
                        "reason", "skipped"),
                List.copyOf(result.keySet()),
                "the result holds the namespaces, the bounds, the reason and the skipped modules, and nothing else");

        groupsOf(result).forEach(group -> assertEquals(
                List.of("root", "importCount", "prefixes", "returnedPrefixes", "totalPrefixes"),
                List.copyOf(group.keySet()),
                "a group holds a namespace root and its counts, no artifact and no version"));
    }
}
