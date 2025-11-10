package com.framstag.llmaj.cli;

import com.framstag.llmaj.tasks.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "clear", description = "Clears the current state",
        mixinStandardHelpOptions = true)
public class StateClearCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(StateClearCmd.class);

    @CommandLine.Parameters(index = "0",description = "Path to the working directory where result of analysis is stored")
    String workingDirectory;

    @Override
    public Integer call() throws Exception {
        logger.info("Clearing state for working directory '{}'...", workingDirectory);

        if (!TaskManager.deleteStateFile(Path.of(workingDirectory))) {
            return 1;
        }

        logger.info("State cleared by deleting state file.");

        return 0;
    }
}
