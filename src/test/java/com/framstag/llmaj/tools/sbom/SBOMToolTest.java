package com.framstag.llmaj.tools.sbom;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.framstag.llmaj.AnalysisContext;

public class SBOMToolTest {
    private static final List<String> CURRENT_DEPENDENCIES = List.of(
        "pkg:maven/ch.qos.logback/logback-classic@1.5.18?type=jar",
        "pkg:maven/com.fasterxml.jackson.dataformat/jackson-dataformat-yaml@2.19.2?type=jar",
        "pkg:maven/com.fasterxml.jackson.datatype/jackson-datatype-jdk8@2.19.2?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j-ollama@1.3.0?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j@1.3.0?type=jar",
        "pkg:maven/info.picocli/picocli@4.7.7?type=jar",
        "pkg:maven/org.cyclonedx/cyclonedx-core-java@10.2.1?type=jar",
        "pkg:maven/org.thymeleaf/thymeleaf@3.1.3.RELEASE?type=jar"
    );

    private static final List<String> CURRENT_LICENSES = List.of(
        "Apache-2.0",
        "BSD-2-Clause",
        "EPL-1.0",
        "GNU Lesser General Public License",
        "LGPL-2.1-only",
        "MIT",
        "MPL-1.1"
    );

    private SBOMTool sbomTool;

    @BeforeEach
    void initializeContext() {
        AnalysisContext context = new AnalysisContext(
                "ArchitectureAnalysis",
                "1.0.0",
                Paths.get("").toAbsolutePath().toString());

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

        Assertions.assertEquals("Error", result);
    }

    @Test
    void correctlyReturnApplicationDependencies() {
        String result = sbomTool.loadSBOM("target/bom.json");

        Assertions.assertEquals("OK", result);

        List<AppDependency> dependencies = sbomTool.getApplicationDependencies();

        List<String> ids = dependencies
            .stream()
            .map(AppDependency::id)
            .sorted()
            .toList();

        Assertions.assertEquals(CURRENT_DEPENDENCIES, ids);
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
