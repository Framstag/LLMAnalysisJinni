package com.framstag.llmaj.cli;

import com.framstag.llmaj.config.ConfigOverrides;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that an option which was not passed can be told apart from an option that was passed
 * with its default value, which is what keeps the workspace configuration in effect.
 */
public class AnalyseCmdOptionParsingTest {

    private static ConfigOverrides parseOverrides(String... arguments) {
        AnalyseCmd analyseCmd = new AnalyseCmd();

        String[] argumentsWithWorkingDirectory = Arrays.copyOf(arguments, arguments.length + 1);
        argumentsWithWorkingDirectory[arguments.length] = "workspace";

        new CommandLine(analyseCmd).parseArgs(argumentsWithWorkingDirectory);

        return analyseCmd.readOverrides();
    }

    @Test
    public void testAbsentOptionsAreNotOverrides() {
        ConfigOverrides overrides = parseOverrides();

        assertNull(overrides.logRequests());
        assertNull(overrides.logResponses());
        assertNull(overrides.executionTrace());
        assertNull(overrides.executionTraceSystem());
        assertNull(overrides.taskParallelism());
    }

    @Test
    public void testPassedOptionIsAnOverride() {
        ConfigOverrides overrides = parseOverrides("--log-response", "true");

        assertEquals(Boolean.TRUE, overrides.logResponses());
        assertNull(overrides.logRequests(), "a different option must stay absent");
    }

    @Test
    public void testPassedDefaultValueIsStillAnOverride() {
        ConfigOverrides overrides = parseOverrides("--log-request", "false",
                "--log-response", "false",
                "--execution-trace", "false",
                "--execution-trace-system", "false");

        assertEquals(Boolean.FALSE, overrides.logRequests());
        assertEquals(Boolean.FALSE, overrides.logResponses());
        assertEquals(Boolean.FALSE, overrides.executionTrace());
        assertEquals(Boolean.FALSE, overrides.executionTraceSystem());
    }

    @Test
    public void testExecutionTraceOverride() {
        ConfigOverrides overrides = parseOverrides("--execution-trace", "true");

        assertEquals(Boolean.TRUE, overrides.executionTrace());
        assertNull(overrides.executionTraceSystem(), "a different option must stay absent");
    }

    @Test
    public void testTaskParallelismOverride() {
        ConfigOverrides overrides = parseOverrides("--task-parallelism", "4");

        assertEquals(4, overrides.taskParallelism());
        assertNull(overrides.logRequests(), "a different option must stay absent");
    }

    private static AnalyseCmd parse(String... arguments) {
        AnalyseCmd analyseCmd = new AnalyseCmd();
        new CommandLine(analyseCmd).parseArgs(arguments);

        return analyseCmd;
    }

    @Test
    public void testExecuteOnlyBeforeWorkingDirectory() {
        AnalyseCmd analyseCmd = parse("-o", "FirstTask", "workspace");

        assertEquals(Set.of("FirstTask"), analyseCmd.executeOnly);
        assertEquals(Path.of("workspace"), analyseCmd.workingDirectory,
                "the option must not consume the workspace directory argument");
    }

    @Test
    public void testExecuteOnlyAfterWorkingDirectory() {
        AnalyseCmd analyseCmd = parse("workspace", "-o", "FirstTask");

        assertEquals(Set.of("FirstTask"), analyseCmd.executeOnly);
        assertEquals(Path.of("workspace"), analyseCmd.workingDirectory);
    }

    @Test
    public void testExecuteOnlyCommaSeparatedList() {
        AnalyseCmd analyseCmd = parse("-o", "FirstTask,SecondTask", "workspace");

        assertEquals(Set.of("FirstTask", "SecondTask"), analyseCmd.executeOnly);
        assertEquals(Path.of("workspace"), analyseCmd.workingDirectory);
    }

    @Test
    public void testExecuteOnlyRepeatedOption() {
        AnalyseCmd analyseCmd = parse("-o", "FirstTask", "-o", "SecondTask", "workspace");

        assertEquals(Set.of("FirstTask", "SecondTask"), analyseCmd.executeOnly);
        assertEquals(Path.of("workspace"), analyseCmd.workingDirectory);
    }

    @Test
    public void testExecuteOnlyAbsent() {
        AnalyseCmd analyseCmd = parse("workspace");

        assertTrue(analyseCmd.executeOnly.isEmpty());
        assertEquals(Path.of("workspace"), analyseCmd.workingDirectory);
    }

    @Test
    public void testMissingWorkingDirectoryIsReportedByName() {
        AnalyseCmd analyseCmd = new AnalyseCmd();
        CommandLine commandLine = new CommandLine(analyseCmd);

        CommandLine.MissingParameterException exception = assertThrows(
                CommandLine.MissingParameterException.class,
                () -> commandLine.parseArgs("-o", "FirstTask"));

        assertTrue(exception.getMessage().contains("<workingDirectory>"),
                "the error must name the missing workspace directory argument, got: " + exception.getMessage());
    }
}
