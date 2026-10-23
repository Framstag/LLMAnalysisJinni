package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.json.ObjectMapperFactory;
import com.framstag.llmaj.state.StateManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regenerates the raw module reports of the local workspaces in the new format. The reports are the source of
 * truth for every Java metric tool, so a workspace whose reports predate the weighted reference record yields
 * nothing for the intra-module analyses until it is regenerated.
 *
 * <p>The workspaces are a local, untracked directory; the regeneration is idempotent and reuses a report that
 * already records the current format. It rewrites a local directory and parses whole project trees, so it runs
 * only when it is asked for: {@code mvn test -Dllmaj.regenerateWorkspaces=true}.
 */
class RegenerateWorkspaceReportsTest {
    private final ObjectMapper objectMapper = ObjectMapperFactory.getJSONObjectMapperInstance();

    @TempDir
    Path tempDir;

    @Test
    void aReportOfAnOlderFormatIsRegenerated() throws Exception {
        Path project = tempDir.resolve("project");
        Path sourceFile = project.resolve("src/main/java/demo/Service.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package demo;

                /** A service of the demo. */
                public class Service {
                    public void run() {
                    }
                }
                """);

        Path reportFile = tempDir.resolve("Java/Java_core.json");
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, """
                {
                  "name" : "core",
                  "reportFormatVersion" : 4,
                  "packages" : [ ]
                }
                """);

        Map<String, Object> result = new JavaTool(contextFor(project)).generateAllModuleAnalysisReports();

        List<?> reports = (List<?>) result.get("reports");
        assertEquals(1, reports.size(), "the module must be reported once");

        Map<?, ?> report = (Map<?, ?>) reports.getFirst();
        assertEquals("GENERATED", report.get("status"),
                "a report of an older format must be regenerated, not reused");
        String reasoning = (String) report.get("reasoning");
        assertTrue(reasoning.contains("format version 4"),
                "the recorded version has to be named, got: " + reasoning);
        assertTrue(reasoning.contains("version 5"),
                "the current version has to be named, got: " + reasoning);
        assertTrue(reasoning.contains("regenerated"),
                "the regeneration has to be named, got: " + reasoning);

        Module regenerated = objectMapper.readValue(reportFile.toFile(), Module.class);
        assertEquals(Module.CURRENT_REPORT_FORMAT_VERSION, regenerated.getReportFormatVersion(),
                "the regenerated report has to record the current format version");
        assertTrue(regenerated.isReportFormatCurrent(),
                "the regenerated report has to be a report of the current format");
    }

    @Test
    void theRegeneratedReportHoldsTheSourceFileOfTheClass() throws Exception {
        Path project = tempDir.resolve("project");
        Path sourceFile = project.resolve("src/main/java/demo/Service.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package demo;

                /** A service of the demo. */
                public class Service {
                    public void run() {
                    }
                }
                """);

        new JavaTool(contextFor(project)).generateAllModuleAnalysisReports();

        Module regenerated = objectMapper.readValue(
                tempDir.resolve("Java/Java_core.json").toFile(), Module.class);

        Clazz clazz = regenerated.getPackages().getFirst()
                .getBuildUnits().getFirst()
                .getClazzes().getFirst();

        assertEquals("src/main/java/demo/Service.java", clazz.getSourceFile(),
                "the class record has to hold the path of the file the class was declared in, relative to the"
                        + " project root");
    }

    /**
     * A workspace of one Java module whose report is missing, so that the report is generated.
     */
    private AnalysisContext contextFor(Path project) {
        ObjectNode state = objectMapper.createObjectNode();
        ArrayNode modules = state.putObject("modules").putArray("modules");

        ObjectNode module = modules.addObject();
        module.put("name", "core");
        module.put("path", "src/main/java");
        module.putObject("subdirectories").putArray("directories");

        ArrayNode languages = module.putObject("programmingLanguages").putArray("programmingLanguages");
        ObjectNode language = languages.addObject();
        language.put("name", "Java");
        language.put("version", "unknown");

        return new AnalysisContext(project, tempDir, Map.of(), state);
    }

    @Test
    void regenerateEveryLocalWorkspace() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("llmaj.regenerateWorkspaces"),
                "run with -Dllmaj.regenerateWorkspaces=true to regenerate the local workspaces");

        Path workspaces = Path.of("workspaces");

        if (!Files.isDirectory(workspaces)) {
            return;
        }

        List<Path> workspaceDirectories;

        try (var entries = Files.list(workspaces)) {
            workspaceDirectories = entries.filter(Files::isDirectory)
                    .filter(directory -> Files.exists(directory.resolve("config.json")))
                    .sorted()
                    .toList();
        }

        for (Path workspace : workspaceDirectories) {
            Config config;

            try {
                config = ConfigLoader.loadFromWorkingDirectory(workspace);
            } catch (IOException e) {
                // A directory under workspaces/ that holds a config of another shape is not a workspace of
                // this tool; there is nothing to regenerate for it.
                System.out.println("workspace " + workspace + ": not a workspace of this tool ("
                        + e.getMessage() + ")");
                continue;
            }

            StateManager stateManager = StateManager.initializeState(workspace);

            AnalysisContext context = new AnalysisContext(config.getProjectDirectory(),
                    workspace,
                    config.getProperties(),
                    stateManager.getAnalysisState());

            Map<String, Object> result = new JavaTool(context).generateAllModuleAnalysisReports();

            List<?> reports = (List<?>) result.get("reports");
            List<?> skipped = (List<?>) result.get("skipped");

            System.out.println("workspace " + workspace + ": " + reports.size() + " report(s), "
                    + skipped.size() + " skipped");
        }
    }
}
