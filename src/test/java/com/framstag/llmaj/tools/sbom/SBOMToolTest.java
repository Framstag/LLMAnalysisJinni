package com.framstag.llmaj.tools.sbom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framstag.llmaj.AnalysisContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SBOMToolTest {
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

    /**
     * The expected direct dependencies are not a hardcoded list (which goes stale on every pom
     * change) but the direct dependencies of the SBOM this build actually generated: the test
     * reads target/bom.json as raw JSON with Jackson, a parser the tool under test does not
     * use, so dependency changes are reflected on both sides.
     */
    @Test
    void correctlyReturnApplicationDependencies() throws Exception {
        String result = sbomTool.loadSBOM("target/bom.json");

        Assertions.assertEquals("OK", result);

        List<AppDependency> dependencies = sbomTool.getApplicationDependencies();

        List<String> ids = dependencies
            .stream()
            .map(AppDependency::id)
            .sorted()
            .toList();

        Assertions.assertEquals(directDependenciesOfBomFile(), ids);

        // A single known core dependency must be present, checked version-agnostically so the
        // anchor itself does not go stale on a version bump. This fails if the selection and its
        // raw-JSON reference collapse to the same wrong result, which the check above would pass.
        Assertions.assertTrue(ids.stream().anyMatch(id -> id.startsWith("pkg:maven/dev.langchain4j/langchain4j@")),
                "the core langchain4j dependency must be part of the selection, got: " + ids);
    }

    private static List<String> directDependenciesOfBomFile() throws Exception {
        JsonNode bom = new ObjectMapper().readTree(new File("target/bom.json"));

        String rootRef = bom.path("metadata").path("component").path("bom-ref").asText();

        List<String> directDependencies = new ArrayList<>();
        bom.path("dependencies").forEach(entry -> {
            if (entry.path("ref").asText().equals(rootRef)) {
                entry.path("dependsOn").forEach(ref -> directDependencies.add(ref.asText()));
            }
        });

        return directDependencies.stream().sorted().toList();
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
