package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.state.StateManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the vocabulary analysis over the local workspaces, which hold whole project trees and are not part of
 * the repository. It answers the two questions no unit test with a hand-built module can answer: does the
 * analysis survive a corpus of tens of thousands of identifiers, and does it produce the same evidence twice.
 *
 * <p>It writes into the local workspaces, so it runs only when it is asked for:
 * {@code mvn test -Dllmaj.domainVocabularyWorkspace=true}.
 */
class DomainVocabularyWorkspaceTest {

    @Test
    void theVocabularyOfTheLocalWorkspacesIsCountedAndStable() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("llmaj.domainVocabularyWorkspace"),
                "run with -Dllmaj.domainVocabularyWorkspace=true to analyse the local workspaces");

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
                // A directory under workspaces/ whose config is of another shape is not a workspace of this
                // tool, and a broken one is reported here instead of failing the analysis of the others.
                System.out.println("workspace " + workspace + ": not usable (" + e.getMessage() + ")");
                continue;
            }

            analyse(workspace, config);
        }
    }

    private void analyse(Path workspace, Config config) throws IOException {
        ObjectNodeHolder holder = ObjectNodeHolder.of(workspace, config);

        Map<String, Object> firstRun = holder.tool.getAllDomainVocabularyReports();
        Map<String, List<String>> evidenceAfterFirstRun = evidenceOf(workspace);

        Map<String, Object> secondRun = holder.tool.getAllDomainVocabularyReports();
        Map<String, List<String>> evidenceAfterSecondRun = evidenceOf(workspace);

        List<?> reports = (List<?>) firstRun.get("reports");
        List<?> skipped = (List<?>) firstRun.get("skipped");
        int modules = holder.moduleCount();

        System.out.println("workspace " + workspace + ": " + reports.size() + " module(s) measured, "
                + skipped.size() + " skipped, of " + modules + " module(s)");
        System.out.println("  lexicon: " + firstRun.get("lexicon"));

        for (Object report : reports) {
            Map<?, ?> descriptor = (Map<?, ?>) report;

            if (!descriptor.containsKey("leadingTerms")) {
                System.out.println("  " + descriptor.get("moduleName") + ": insufficient evidence, "
                        + descriptor.get("candidateTerms") + " candidate term(s)");
                continue;
            }

            System.out.println("  " + descriptor.get("moduleName")
                    + ": " + descriptor.get("candidateTerms") + " candidate term(s),"
                    + " technical share " + descriptor.get("technicalShare")
                    + ", naming cohesion " + descriptor.get("namingCohesion")
                    + ", purpose coverage " + descriptor.get("purposeCoverage")
                    + ", leading terms " + descriptor.get("leadingTerms"));
        }

        assertEquals(modules, reports.size() + skipped.size(),
                "every module of the workspace is either measured or named as skipped");

        assertEquals(evidenceAfterFirstRun.keySet(), evidenceAfterSecondRun.keySet(),
                "both runs write the same evidence files");
        assertEquals(evidenceAfterFirstRun, evidenceAfterSecondRun,
                "two runs over the same module reports produce identical evidence");

        assertTrue(reports.isEmpty() || !evidenceAfterFirstRun.isEmpty(),
                "a measured module leaves its evidence beside the reports");
    }

    private Map<String, List<String>> evidenceOf(Path workspace) throws IOException {
        Map<String, List<String>> evidence = new LinkedHashMap<>();

        for (String reportType : List.of("DomainVocabulary", "NamingCohesion", "DomainLanguageMetrics")) {
            Path directory = workspace.resolve(reportType);

            if (!Files.isDirectory(directory)) {
                continue;
            }

            try (var entries = Files.list(directory)) {
                for (Path file : entries.sorted().toList()) {
                    evidence.put(workspace.relativize(file).toString(),
                            Files.readAllLines(file, StandardCharsets.UTF_8));
                }
            }
        }

        return evidence;
    }

    /**
     * The analysis context and the tool of one workspace, so that a test method stays readable.
     */
    private record ObjectNodeHolder(JavaTool tool, int moduleCount) {

        static ObjectNodeHolder of(Path workspace, Config config) {
            StateManager stateManager = StateManager.initializeState(workspace);

            AnalysisContext context = new AnalysisContext(config.getProjectDirectory(),
                    workspace,
                    config.getProperties(),
                    stateManager.getAnalysisState());

            int moduleCount = 0;

            if (context.getAnalysisState() != null) {
                var modules = context.getAnalysisState().path("modules").path("modules");

                if (modules.isArray()) {
                    moduleCount = modules.size();
                }
            }

            return new ObjectNodeHolder(new JavaTool(context), moduleCount);
        }
    }
}
