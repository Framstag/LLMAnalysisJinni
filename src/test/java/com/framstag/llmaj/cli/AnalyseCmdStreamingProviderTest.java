package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The streaming setting is only served by the OpenAI provider. A workspace that enables it for
 * another provider must end the run before the first task step, and it must say so in one reported
 * error instead of a stack trace.
 */
public class AnalyseCmdStreamingProviderTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path projectDirectory;

    @TempDir
    Path analysisDirectory;

    @TempDir
    Path workspaceDirectory;

    /**
     * One run of the command, with the console it wrote captured.
     */
    private record Run(int exitCode, String output) {
    }

    private void writeConfig(String modelProvider, boolean useStreaming) throws IOException {
        Files.writeString(workspaceDirectory.resolve("config.json"), """
                {
                  "modelProvider": "%s",
                  "modelURL": "http://localhost:11434",
                  "modelName": "test-model",
                  "projectDirectory": %s,
                  "analysisDirectory": %s,
                  "useStreaming": %s
                }
                """.formatted(modelProvider,
                mapper.writeValueAsString(projectDirectory.toString()),
                mapper.writeValueAsString(analysisDirectory.toString()),
                useStreaming));
    }

    private Run runAnalysis() throws Exception {
        AnalyseCmd analyseCmd = new AnalyseCmd();
        new CommandLine(analyseCmd).parseArgs(workspaceDirectory.toString());

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        var captured = new ByteArrayOutputStream();

        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

        try {
            return new Run(analyseCmd.call(), captured.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private void assertRefusedBeforeAnyStep(String provider, Run run) {
        assertEquals(1, run.exitCode(), "the run must end with a failure exit code");
        assertTrue(run.output().contains("Streaming is not supported for the " + provider + " provider"),
                "the error must name the setting and the provider, got: " + run.output());
        assertFalse(run.output().contains("IllegalStateException"),
                "the refusal is reported as an error line, not as a stack trace: " + run.output());
        assertFalse(Files.exists(workspaceDirectory.resolve("state.json")),
                "no task step may have run, so no execution state may have been written");
    }

    @Test
    public void streamingWithOllamaEndsTheRunBeforeTheFirstTaskStep() throws Exception {
        writeConfig("ollama", true);

        assertRefusedBeforeAnyStep("ollama", runAnalysis());
    }

    @Test
    public void streamingWithLocalAiEndsTheRunBeforeTheFirstTaskStep() throws Exception {
        writeConfig("localai", true);

        assertRefusedBeforeAnyStep("localai", runAnalysis());
    }

    /**
     * The refusal covers the setting and not the provider: the same workspace without the setting
     * gets past the model factory, so the run reaches the task list instead.
     */
    @Test
    public void theSameProviderRunsWithoutStreaming() throws Exception {
        writeConfig("ollama", false);
        writeInactiveAnalysis();

        Run run = runAnalysis();

        assertFalse(run.output().contains("Streaming is not supported"),
                "the provider must not be refused while streaming is disabled: " + run.output());
        assertTrue(run.output().contains("Nothing to run"),
                "the run must get past the model factory and reach the task list, got: " + run.output());
    }

    /**
     * An analysis whose only task is inactive: the task list loads, and nothing has to be executed
     * against a model that is not there.
     */
    private void writeInactiveAnalysis() throws IOException {
        Files.writeString(analysisDirectory.resolve("tasks.yaml"), """
                ---
                id: InactiveTask
                name: Inactive Task
                prompt: prompts/single.md
                responseFormat: results/Single.json
                responseProperty: singleResult
                active: false
                tags:
                  - unused
                """);

        Files.createDirectories(analysisDirectory.resolve("prompts"));
        Files.writeString(analysisDirectory.resolve("prompts/single.md"), "Answer the question.");

        Files.createDirectories(analysisDirectory.resolve("results"));
        Files.writeString(analysisDirectory.resolve("results/Single.json"), """
                {"type":"object","properties":{"answer":{"type":"string"}},"required":["answer"]}
                """);
    }
}