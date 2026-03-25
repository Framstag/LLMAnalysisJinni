package com.framstag.llmaj.cli;

import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigLoader;
import com.framstag.llmaj.state.StateManager;
import com.framstag.llmaj.tools.ToolServiceFactory;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "dump", description = "Dump tools",
        mixinStandardHelpOptions = true)
public class ToolsDumpCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ToolsDumpCmd.class);

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

        StateManager stateManager = StateManager.initializeState(workingDirectory);

        AnalysisContext analysisContext = new AnalysisContext(
                config.getProjectDirectory(),
                workingDirectory,
                stateManager.getAnalysisState());

        ToolService toolService = ToolServiceFactory.getToolService(config,analysisContext);

        logger.info("List of tools: ");
        for (ToolSpecification specification : toolService.toolSpecifications()) {
            logger.info("- {}", specification.name());
        }
        logger.info("done.");

        return 0;
    }
}
