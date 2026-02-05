package com.framstag.llmaj.tools.sbom;

import com.framstag.llmaj.AnalysisContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

public class SBOMToolTest {
    private static final List<String> CURRENT_DEPENDENCIES = List.of(
        "pkg:maven/ch.qos.logback/logback-classic@1.5.22?type=jar",
        "pkg:maven/com.fasterxml.jackson.dataformat/jackson-dataformat-yaml@2.20.1?type=jar",
        "pkg:maven/com.fasterxml.jackson.datatype/jackson-datatype-jdk8@2.20.1?type=jar",
        "pkg:maven/com.fasterxml.jackson.datatype/jackson-datatype-jsr310@2.20.1?type=jar",
        "pkg:maven/com.github.javaparser/javaparser-symbol-solver-core@3.27.1?type=jar",
        "pkg:maven/com.github.jknack/handlebars@4.5.0?type=jar",
        "pkg:maven/de.siegmar/fastcsv@4.1.0?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j-mcp@1.11.0-beta19?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j-ollama@1.11.0?type=jar",
        "pkg:maven/dev.langchain4j/langchain4j@1.11.0?type=jar",
        "pkg:maven/info.picocli/picocli@4.7.7?type=jar",
        "pkg:maven/org.cyclonedx/cyclonedx-core-java@11.0.1?type=jar"
    );

    private static final List<String> CURRENT_LICENSES = List.of(
        "Apache-2.0",
        "BSD-2-Clause",
        "BSD-3-Clause",
        "EPL-1.0",
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
