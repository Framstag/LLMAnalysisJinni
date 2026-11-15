package com.framstag.llmaj.tools.sbom;

import com.fasterxml.jackson.databind.JsonNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.json.ObjectMapperFactory;
import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class SBOMToolTest {
    private static final List<String> CURRENT_DEPENDENCIES = List.of(
        "pkg:maven/ch.qos.logback/logback-classic@1.6.3?type=jar",
        "pkg:maven/com.fasterxml.jackson.dataformat/jackson-dataformat-yaml@2.22.2?type=jar",
        "pkg:maven/com.fasterxml.jackson.datatype/jackson-datatype-jdk8@2.22.2?type=jar",
        "pkg:maven/com.fasterxml.jackson.datatype/jackson-datatype-jsr310@2.22.2?type=jar",
        "pkg:maven/com.github.javaparser/javaparser-symbol-solver-core@3.28.2?type=jar",
        "pkg:maven/com.github.jknack/handlebars@4.5.5?type=jar",
        "pkg:maven/com.networknt/json-schema-validator@3.0.7?type=jar",
        "pkg:maven/de.siegmar/fastcsv@4.4.0?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j-local-ai@1.21.0-beta31?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j-mcp@1.21.0-beta31?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j-ollama@1.21.0?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j-open-ai@1.21.0?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j@1.21.0?type=jar",
        "pkg:maven/info.picocli/picocli@4.7.7?type=jar",
        "pkg:maven/org.cyclonedx/cyclonedx-core-java@13.2.0?type=jar",
        "pkg:maven/org.jline/jline-terminal-ffm@4.4.3?type=jar",
        "pkg:maven/org.jline/jline-terminal@4.4.3?type=jar"
    );

    private static final List<String> CURRENT_LICENSES = List.of(
        "Apache-2.0",
        "BSD-2-Clause",
        "BSD-3-Clause",
        "BSD-4-Clause",
        "EPL-2.0",
        "GNU Lesser General Public License",
        "GPL v2 with the Classpath exception",
        "LGPL-2.1-only",
        "MIT",
        "MPL-1.1"
    );

    private SBOMTool sbomTool;

    @BeforeEach
    void initializeContext() {
        AnalysisContext context = new AnalysisContext(
                Paths.get("").toAbsolutePath(),
                Paths.get("").toAbsolutePath(),
                Collections.emptyMap(),
                null);

        sbomTool = new SBOMTool(context);
    }

    @Test
    void successfullyLoadExistingSBOM() {
        String result = sbomTool.loadSBOM("target/bom.json");

        Assertions.assertEquals("OK", result);
    }
    @Test
    void returnErrorOnLoadingNonExistantSBOM() {
        String result = sbomTool.loadSBOM("nonexisting.json");

        Assertions.assertTrue(result.contains("nonexisting.json"),
                "the tool result must name the file it could not load, got: " + result);
        Assertions.assertTrue(result.contains("does not exist"),
                "the tool result must name the condition, got: " + result);
    }

    /**
     * A project without an SBOM reaches the tool with the empty path the location task stored. The
     * model has to be told what that means instead of having to guess from a bare error.
     */
    @Test
    void emptyFilenameNamesTheMissingInput() {
        String result = sbomTool.loadSBOM("");

        Assertions.assertTrue(result.contains("No SBOM file to load"),
                "the tool result must name the missing input, got: " + result);
        Assertions.assertTrue(result.contains("no SBOM was located"),
                "the tool result must say what the empty path means, got: " + result);
    }

    @Test
    void correctlyReturnApplicationDependencies() {
        String result = sbomTool.loadSBOM("target/bom.json");

        Assertions.assertEquals("OK", result);

        AppDependencyRead read = sbomTool.getApplicationDependencies();

        List<String> ids = read.dependencies()
            .stream()
            .map(AppDependency::id)
            .sorted()
            .toList();

            Assertions.assertEquals(CURRENT_DEPENDENCIES, ids);

        // This project's own SBOM is written by the CycloneDX Maven plugin, which writes the entry of the root
        // component, so the direct route is the one the read takes and it has no reason to state.
        Assertions.assertEquals(AppDependencyRead.ROUTE_ROOT, read.route());
        Assertions.assertTrue(read.isDirectDependencyList());
        Assertions.assertEquals("", read.reason(), "the direct route states no reason");
    }

    /**
     * The CycloneDX Gradle plugin writes one graph entry per project module of an aggregator build and none for the
     * aggregator itself. Requiring the entry of the root component therefore reported no dependency for a project
     * whose SBOM holds them all.
     */
    @Test
    void anAggregatorSbomWithoutARootEntryReportsTheDependenciesOfItsModules() {
        SBOMTool tool = toolForProjectRoot(Paths.get("src/test/resources"));

        Assertions.assertEquals("OK", tool.loadSBOM("sbom/aggregator.cdx.json"));

        AppDependencyRead read = tool.getApplicationDependencies();

        Assertions.assertEquals(AppDependencyRead.ROUTE_PROJECT_MODULES, read.route());
        Assertions.assertEquals(List.of(
                        "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.17.2?type=jar",
                        "pkg:maven/org.junit.jupiter/junit-jupiter@5.10.2?type=jar",
                        "pkg:maven/org.slf4j/slf4j-api@2.0.16?type=jar"),
                read.dependencies().stream().map(AppDependency::id).sorted().toList(),
                "the dependencies of both project modules are reported once each");
        Assertions.assertEquals(1, read.projectComponentsLeftOut(),
                "the module the other module depends on is a module of the project and no dependency");
        Assertions.assertEquals(5, read.componentsInSbom());
        Assertions.assertTrue(read.reason().contains("no entry for the reference of the root component"),
                read.reason());
    }

    @Test
    void anSbomWithoutGraphReportsItsComponentsAndSaysItIsNoDirectList() {
        SBOMTool tool = toolForProjectRoot(Paths.get("src/test/resources"));

        Assertions.assertEquals("OK", tool.loadSBOM("sbom/no-graph.cdx.json"));

        AppDependencyRead read = tool.getApplicationDependencies();

        Assertions.assertEquals(AppDependencyRead.ROUTE_COMPONENTS, read.route());
        Assertions.assertEquals(List.of(
                        "pkg:maven/org.apache.commons/commons-lang3@3.14.0?type=jar",
                        "pkg:maven/org.slf4j/slf4j-api@2.0.16?type=jar"),
                read.dependencies().stream().map(AppDependency::id).sorted().toList());
        Assertions.assertFalse(read.isDirectDependencyList(),
                "the components of an SBOM do not tell a direct dependency from a transitive one");
        Assertions.assertTrue(read.reason().contains("not a direct dependency list"), read.reason());
        Assertions.assertEquals(1, read.projectComponentsLeftOut());
    }

    /**
     * A read that finds nothing states the condition that holds. Saying that the project declares no dependency
     * would be a statement about the project, and the read knows nothing about the project.
     */
    @Test
    void aReadWithoutALibraryNamesTheConditionAndNotTheProject() {
        SBOMTool tool = toolForProjectRoot(Paths.get("src/test/resources"));

        Assertions.assertEquals("OK", tool.loadSBOM("sbom/components-without-library.cdx.json"));

        AppDependencyRead read = tool.getApplicationDependencies();

        Assertions.assertTrue(read.dependencies().isEmpty());
        Assertions.assertTrue(read.statesReason(), "an empty read has to state why it is empty");
        Assertions.assertTrue(read.reason().contains("No dependency of the application was found this way"),
                read.reason());
        Assertions.assertFalse(read.reason().toLowerCase().contains("declares no dependency"),
                "the read must not state something about the project: " + read.reason());
    }

    @Test
    void aReadBeforeLoadingNamesTheMissingSbom() {
        SBOMTool tool = toolForProjectRoot(Paths.get("src/test/resources"));

        AppDependencyRead read = tool.getApplicationDependencies();

        Assertions.assertEquals(AppDependencyRead.ROUTE_NONE, read.route());
        Assertions.assertTrue(read.dependencies().isEmpty());
        Assertions.assertTrue(read.reason().contains("SBOM is not loaded"), read.reason());
    }

    /**
     * The tool result reaches the model as JSON, so the route and the reason have to be part of it.
     */
    @Test
    void theResultCarriesTheRouteAndTheReasonAsAProperty() throws Exception {
        SBOMTool tool = toolForProjectRoot(Paths.get("src/test/resources"));

        Assertions.assertEquals("OK", tool.loadSBOM("sbom/aggregator.cdx.json"));

        String serialised = ObjectMapperFactory.getJSONObjectMapperInstance()
                .writeValueAsString(tool.getApplicationDependencies());

        JsonNode node = ObjectMapperFactory.getJSONObjectMapperInstance().readTree(serialised);

        Assertions.assertTrue(node.has("route"), serialised);
        Assertions.assertEquals(AppDependencyRead.ROUTE_PROJECT_MODULES, node.get("route").asText());
        Assertions.assertTrue(node.has("reason"), serialised);
        Assertions.assertTrue(node.has("dependencies"), serialised);
        Assertions.assertEquals(3, node.get("dependencies").size());
        Assertions.assertEquals(5, node.get("componentsInSbom").asInt());
        Assertions.assertEquals(1, node.get("projectComponentsLeftOut").asInt());
    }

    /**
     * The old read looked the reference of the root component up in the graph and returned nothing when the graph
     * held no such entry. The fixture holds no such entry, so this test keeps the regression reproducible: it fails
     * the read that required the entry and it proves that the fallback is the route under test.
     */
    @Test
    void theFixtureHoldsTheLayoutTheRequiredEntryFailedOn() throws Exception {
        Bom bom = new JsonParser().parse(Paths.get("src/test/resources/sbom/aggregator.cdx.json").toFile());

        String rootReference = bom.getMetadata().getComponent().getBomRef();

        Assertions.assertTrue(
                bom.getDependencies().stream().noneMatch(entry -> rootReference.equals(entry.getRef())),
                "the fixture must hold no graph entry for the root component, so that it fails a read which requires"
                        + " that entry");
        Assertions.assertFalse(bom.getDependencies().isEmpty(),
                "the fixture must hold a graph, so that it exercises the module route and not the component route");
    }

    private SBOMTool toolForProjectRoot(Path projectRoot) {
        AnalysisContext context = new AnalysisContext(
                projectRoot.toAbsolutePath(),
                Paths.get("").toAbsolutePath(),
                Collections.emptyMap(),
                null);

        return new SBOMTool(context);
    }

    @Test
    void correctlyListDependencyLicenses() {
        String result = sbomTool.loadSBOM("target/bom.json");

        Assertions.assertEquals("OK", result);

        List<License> licenses = sbomTool.getApplicationDependencyLicences();

        List<String> licenseStrings = licenses
            .stream()
            .map(License::description)
            .sorted()
            .toList();

        Assertions.assertEquals(CURRENT_LICENSES, licenseStrings);
    }
}
