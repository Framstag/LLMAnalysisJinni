package com.framstag.llmaj.cli;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.tasks.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Callable;

@Command(name = "dump", description = "Dump current execution state",
        mixinStandardHelpOptions = true)
public class StateDumpCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(StateDumpCmd.class);

    @CommandLine.Parameters(index = "0",description = "Path to the working directory")
    Path workingDirectory;

    @Override
    public Integer call() throws Exception {
        Config config;

        try {
            config = ConfigLoader.load(workingDirectory);
        } catch (IOException e) {
            logger.error("Cannot load config file", e);
            return 1;
        }

        TaskManager taskManager = TaskManager.initializeTasks(config.getAnalysisDirectory(),
                workingDirectory,
                Collections.emptySet());

        taskManager.dump();

        return 0;
    }
}
