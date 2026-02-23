package com.framstag.llmaj.cli;

import com.framstag.llmaj.config.Config;
import com.framstag.llmaj.config.ConfigStorer;
import com.framstag.llmaj.file.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "init", description = "Initialize a new workspace",
        mixinStandardHelpOptions = true)
public class WorkspaceInitCmd implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceInitCmd.class);

    @CommandLine.Option(names={"-u","--modelUrl"}, required = true, arity = "1", description = "The URL of the ollama server")
    URL modelUrl;

    @CommandLine.Option(names={"-m","--model"}, required = true, arity = "1", description = "The name of the model to use")
    String modelName;

    @CommandLine.Option(names={"-j","--native-json"}, arity = "1", defaultValue = "false", description = "Enforce json response")
    boolean jsonResponse = false;

    @CommandLine.Option(names={"--chatWindowSize"}, arity = "1", defaultValue="50", description = "The number of messages to memorize between prompts")
    int chatWindowSize;

    @CommandLine.Option(names={"--log-request"}, arity = "1", defaultValue = "false", description = "Activate langchain4j low-level log of chat requests")
    boolean logRequest = false;

    @CommandLine.Option(names={"--log-response"}, arity = "1", defaultValue = "false", description = "Activate langchain4j low-level log of chat responses")
    boolean logResponse = false;

    @CommandLine.Option(names={"-p","--project"}, required = true, arity = "0..1", description = "Path to the root directory of the project to analyse")
    Path projectRoot;

    @CommandLine.Option(names={"-a","--analysis"}, required = true, arity = "1", description = "Path to the analysis description directory")
    Path analysisDirectory;

    @CommandLine.Parameters(index = "0",description = "Path to the working directory where result of analysis is stored")
    Path workingDirectory;

    @Override
    public Integer call() throws Exception {
        logger.info("Initialize working directory '{}'...", workingDirectory);

        if (!FileHelper.isDirectoryAndCanBeReadFrom(projectRoot,
                "project",
                "Project")) {
            return 1;
        }

        if (!FileHelper.isDirectoryAndCanBeReadFrom(analysisDirectory,
                "analysis",
                "Analysis")) {
            return 1;
        }

        if (!FileHelper.isDirectoryAndCanBeWrittenTo(workingDirectory,
                "workspace",
                "Workspace")) {
            return 1;
        }

        Config config = new Config();

        config.setModelURL(modelUrl);
        config.setModelName(modelName);
        config.setChatWindowSize(chatWindowSize);
        config.setNativeJSON(jsonResponse);
        config.setLogRequests(logRequest);
        config.setLogResponses(logResponse);
        config.setProjectDirectory(projectRoot);
        config.setAnalysisDirectory(analysisDirectory);

        config.dumpToLog();

        ConfigStorer.save(config, workingDirectory);

        logger.info("Workspace initialized.");

        return 0;
    }
}
