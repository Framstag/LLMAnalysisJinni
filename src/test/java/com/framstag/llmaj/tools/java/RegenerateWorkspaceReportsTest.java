package com.framstag.llmaj.tools.java;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.state.StateManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
