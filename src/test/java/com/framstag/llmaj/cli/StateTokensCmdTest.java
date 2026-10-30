package com.framstag.llmaj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The token report of a workspace: the total, the usage of every task, the breakdown of a loop
 * task, and the same data as JSON.
 */
public class StateTokensCmdTest {

    @TempDir
    Path tempDir;

    /**
     * Runs the command against a workspace whose execution state is the given text and returns
     * what it wrote to the console.
     */
    private String run(String stateJson, String... arguments) throws Exception {
        Path workspace = writeWorkspace(stateJson);

        StateTokensCmd cmd = new StateTokensCmd();
        cmd.workingDirectory = workspace;

        for (String argument : arguments) {
            if (argument.equals("--json")) {
                cmd.json = true;
            }
        }

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

            int exitCode = cmd.call();

            assertEquals(0, exitCode, "the report must succeed");
        } finally {
            System.setOut(originalOut);
        }

        return captured.toString(StandardCharsets.UTF_8);
    }

    /**
     * Runs the command against a workspace that already exists.
     */
    private String run(String stateJson, Path workspace) throws Exception {
        StateTokensCmd cmd = new StateTokensCmd();
        cmd.workingDirectory = workspace;

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));

            assertEquals(0, cmd.call(), "the report must succeed");
        } finally {
            System.setOut(originalOut);
        }

        return captured.toString(StandardCharsets.UTF_8);
    }

    /**
     * Writes a workspace whose execution state is the given text.
     */
    private Path writeWorkspace(String stateJson) throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);

        Files.writeString(workspace.resolve("config.json"), """
                {
                  "modelProvider": "ollama",
                  "modelURL": "http://localhost:11434",
                  "modelName": "test-model",
                  "projectDirectory": ".",
                  "analysisDirectory": "analysis/software-architecture"
                }
                """);

        if (stateJson != null) {
            Files.writeString(workspace.resolve("state.json"), stateJson);
        }

        return workspace;
    }

    private static final String STATE_WITH_USAGES = """
            [ {
              "taskId" : "BuildSystems",
              "lastExecution" : "2026-10-06T20:02:52.891+0000",
              "state" : "SUCCESSFUL",
              "tokenUsage" : { "inputTokens" : 21001, "outputTokens" : 20883, "totalTokens" : 41884 }
            }, {
              "taskId" : "DroppedModuleTask",
              "lastExecution" : "2026-10-20T10:02:52.891+0000",
              "state" : "SUCCESSFUL",
              "tokenUsage" : { "inputTokens" : 900000, "outputTokens" : 400000, "totalTokens" : 1300000 },
              "indexTokenUsage" : {
                "0" : { "inputTokens" : 450000, "outputTokens" : 200000, "totalTokens" : 650000 },
                "1" : { "inputTokens" : 450000, "outputTokens" : 200000, "totalTokens" : 650000 }
              }
            } ]
            """;

    @Test
    public void theReportNamesTheTotalOfTheWorkspace() throws Exception {
        String report = run(STATE_WITH_USAGES);

        assertTrue(report.contains("TOTAL"), "the report must name the total, got:\n" + report);
        assertTrue(report.contains("921,001"), "the total input count, got:\n" + report);
        assertTrue(report.contains("420,883"), "the total output count, got:\n" + report);
        assertTrue(report.contains("1,341,884"), "the total count, got:\n" + report);
    }

    @Test
    public void theReportNamesEveryTask() throws Exception {
        String report = run(STATE_WITH_USAGES);

        assertTrue(report.contains("BuildSystems"), "the report must name the task, got:\n" + report);
        assertTrue(report.contains("21,001"), "the usage of the task, got:\n" + report);
        assertTrue(report.contains("DroppedModuleTask"), "the report must name the task, got:\n" + report);
    }

    @Test
    public void theReportNamesTheIndicesOfALoopTask() throws Exception {
        String report = run(STATE_WITH_USAGES);

        assertTrue(report.contains("index 0"), "the report must name the index, got:\n" + report);
        assertTrue(report.contains("index 1"), "the report must name the index, got:\n" + report);
        assertTrue(report.contains("450,000"), "the usage of the index, got:\n" + report);
    }

    @Test
    public void aWorkspaceWithoutUsagesIsReportedAsSuch() throws Exception {
        String report = run("""
                [ {
                  "taskId" : "BuildSystems",
                  "lastExecution" : "2026-10-06T20:02:52.891+0000",
                  "state" : "SUCCESSFUL"
                } ]
                """);

        assertTrue(report.contains("No token usage is recorded"),
                "the report must say that nothing is recorded, got:\n" + report);
    }

    @Test
    public void aWorkspaceWithoutAStateFileIsReportedAsSuch() throws Exception {
        String report = run(null);

        assertTrue(report.contains("No token usage is recorded"),
                "the report must say that nothing is recorded, got:\n" + report);
    }

    @Test
    public void theReportIsAlsoAvailableAsJson() throws Exception {
        String report = run(STATE_WITH_USAGES, "--json");

        assertTrue(report.contains("\"taskId\""), "the report must be JSON, got:\n" + report);
        assertTrue(report.contains("\"inputTokens\" : 21001"),
                "the report must hold the usage of the task, got:\n" + report);
        assertTrue(report.contains("\"totalTokens\" : 1341884"),
                "the report must hold the total, got:\n" + report);
        assertTrue(report.contains("\"index\" : 0"),
                "the report must hold the indices, got:\n" + report);
    }

    @Test
    public void theJsonReportOfAWorkspaceWithoutUsagesHoldsNoCounts() throws Exception {
        String report = run(null, "--json");

        assertTrue(report.contains("\"inputTokens\" : null"),
                "a count that is not known is not a count of zero, got:\n" + report);
        assertTrue(report.contains("\"tasks\" : [ ]"),
                "the report must hold no task, got:\n" + report);
    }

    @Test
    public void theTasksAndTheirIndicesAreOrdered() throws Exception {
        // The tasks of a workspace are recorded in the order they finished, and the indices of a
        // loop task in the order their workers completed them.
        String report = run("""
                [ {
                  "taskId" : "ZebraTask",
                  "lastExecution" : "2026-10-06T20:02:52.891+0000",
                  "state" : "SUCCESSFUL",
                  "tokenUsage" : { "inputTokens" : 1, "outputTokens" : 1, "totalTokens" : 2 },
                  "indexTokenUsage" : {
                    "10" : { "inputTokens" : 1, "outputTokens" : 1, "totalTokens" : 2 },
                    "2" : { "inputTokens" : 1, "outputTokens" : 1, "totalTokens" : 2 },
                    "1" : { "inputTokens" : 1, "outputTokens" : 1, "totalTokens" : 2 }
                  }
                }, {
                  "taskId" : "AlphaTask",
                  "lastExecution" : "2026-10-06T20:02:52.891+0000",
                  "state" : "SUCCESSFUL",
                  "tokenUsage" : { "inputTokens" : 1, "outputTokens" : 1, "totalTokens" : 2 }
                } ]
                """);

        assertTrue(report.indexOf("AlphaTask") < report.indexOf("ZebraTask"),
                "the tasks must be ordered by their id, got:\n" + report);
        assertTrue(report.indexOf("index 1") < report.indexOf("index 2"),
                "the indices must be ordered by their index, got:\n" + report);
        assertTrue(report.indexOf("index 2") < report.indexOf("index 10"),
                "the indices must be ordered by their index, got:\n" + report);
    }

    @Test
    public void droppingATaskRemovesItsRecordedUsage() throws Exception {
        Path workspace = writeWorkspace(STATE_WITH_USAGES);

        StateDropCmd dropCmd = new StateDropCmd();
        dropCmd.workingDirectory = workspace;
        dropCmd.taskNames = List.of("DroppedModuleTask");

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        try {
            assertEquals(0, dropCmd.call());
        } finally {
            System.setOut(originalOut);
        }

        String report = run(STATE_WITH_USAGES, workspace);

        assertFalse(report.contains("DroppedModuleTask"),
                "the usage of a dropped task is no longer reported, got:\n" + report);
        assertTrue(report.contains("BuildSystems"),
                "the usage of the other tasks is untouched, got:\n" + report);
    }

    @Test
    public void clearingTheStateRemovesEveryRecordedUsage() throws Exception {
        Path workspace = writeWorkspace(STATE_WITH_USAGES);

        StateClearCmd clearCmd = new StateClearCmd();
        clearCmd.workingDirectory = workspace.toString();

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        try {
            assertEquals(0, clearCmd.call());
        } finally {
            System.setOut(originalOut);
        }

        String report = run(null, workspace);

        assertTrue(report.contains("No token usage is recorded"),
                "no usage is reported after the state was cleared, got:\n" + report);
    }
}
