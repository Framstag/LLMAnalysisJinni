package com.framstag.llmaj.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framstag.llmaj.AnalysisContext;
import com.framstag.llmaj.state.StateManager;
import com.framstag.llmaj.tools.java.JavaTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "java-analyse", description = "Analysize a Java module",
        mixinStandardHelpOptions = true)
public class JavaAnalysisCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(JavaAnalysisCmd.class);

    @CommandLine.Parameters(index = "0",description = "Path to the root directory of the project to analyse")
    String projectRoot;

    @CommandLine.Parameters(index = "1",description = "Path to the working directory where result of analysis is stored")
    String workingDirectory;

    @CommandLine.Parameters(index = "2",description = "Name of the module to analyze")
    String moduleName;

    @Override
    public Integer call() throws Exception {
        logger.info("Triggering java analysis for module '{}' in project '{}' based on state in '{}'...",
                moduleName,
                projectRoot,
                workingDirectory);

        StateManager stateManager = StateManager.initializeState(Path.of(workingDirectory));

        ObjectNode state = stateManager.getAnalysisState();

        AnalysisContext context = new AnalysisContext( Path.of(projectRoot),
                Path.of(workingDirectory), state);

        JavaTool javaTool = new JavaTool(context);

        javaTool.generateModuleAnalysisReport(moduleName);

        return 0;
    }
}
