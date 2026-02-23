package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.state.StateManager;
import com.framstag.llmaj.tools.java.JavaTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "java-analyse", description = "Analysize a Java module",
        mixinStandardHelpOptions = true)
public class JavaAnalysisCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(JavaAnalysisCmd.class);

    @CommandLine.Parameters(index = "0",description = "Path to the working directory where result of analysis is stored")
    Path workingDirectory;

    @CommandLine.Parameters(index = "1",description = "Name of the module to analyze")
    String moduleName;

    @Override
    public Integer call() throws Exception {
        Config config;

        try {
            logger.info("Loading config from workspace '{}'...", workingDirectory);
            config = ConfigLoader.load(workingDirectory);
            config.dumpToLog();
        } catch (IOException e) {
            logger.error("Cannot load config file", e);
            return 1;
        }

        logger.info("Triggering java analysis for module '{}' in project '{}' based on state in '{}'...",
                moduleName,
                config.getProjectDirectory(),
                workingDirectory);

        StateManager stateManager = StateManager.initializeState(workingDirectory);

        ObjectNode state = stateManager.getAnalysisState();

        AnalysisContext context = new AnalysisContext(config.getProjectDirectory(),
                workingDirectory, state);

        JavaTool javaTool = new JavaTool(context);

        javaTool.generateModuleAnalysisReport(moduleName);

        return 0;
    }
}
